package edu.college.nss.schema;

import edu.college.nss.security.DatabaseIdentityGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.flywaydb.core.Flyway;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Catalog and behavioral verification asserting the exact structure, column attribution,
 * and status invariants of check constraints on the service_hour_entries table in PostgreSQL.
 *
 * Invariants proven:
 * 1. chk_service_hour_entries_status binds strictly to ['status'] (no other columns in conkey).
 * 2. Allowed status set extracted from ARRAY[...] definition is strictly and exactly
 *    {'APPROVED', 'PENDING', 'REJECTED', 'REVOKED'} (no extra or missing statuses).
 * 3. Behavioral verification: all 4 intended statuses are accepted; representative unintended
 *    values ('INVALID', 'SUSPENDED', 'EXCUSED', 'DELETED', 'DRAFT', 'UNKNOWN', 'approved', '')
 *    are rejected with SQLSTATE 23514 (check_violation).
 * 4. service_hour_entries_hours_check binds strictly to ['hours'].
 */
@Tag("postgres-concurrency")
@DisplayName("PostgreSQL Catalog and Behavioral Assertion for Service Hour Check Constraints")
public class ServiceHourSchemaConstraintTest {

    private static PostgreSQLContainer<?> postgreSqlContainer;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;
    private static boolean environmentAvailable = false;

    private static final Set<String> EXPECTED_STATUSES = Set.of(
        "APPROVED",
        "PENDING",
        "REJECTED",
        "REVOKED"
    );

    @BeforeAll
    static void initDatabaseEnvironment() {
        String customTestUrl = System.getenv("POSTGRES_TEST_URL");
        if (customTestUrl == null || customTestUrl.isBlank()) {
            customTestUrl = System.getProperty("postgres.test.url");
        }

        if (customTestUrl != null && !customTestUrl.isBlank()) {
            // Strict positive test identity verification
            DatabaseIdentityGuard.assertSafeTestDatabase(customTestUrl);
            dbUrl = customTestUrl;
            dbUser = System.getenv().getOrDefault("POSTGRES_TEST_USERNAME", "postgres");
            dbPass = System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "postgres");
            runFlywayMigrations(dbUrl, dbUser, dbPass);
            environmentAvailable = true;
            return;
        }

        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                postgreSqlContainer = new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("rec_nss_schema_test")
                    .withUsername("testuser")
                    .withPassword("testpass");
                postgreSqlContainer.start();

                dbUrl = postgreSqlContainer.getJdbcUrl();
                dbUser = postgreSqlContainer.getUsername();
                dbPass = postgreSqlContainer.getPassword();

                DatabaseIdentityGuard.assertSafeTestDatabase(dbUrl);
                runFlywayMigrations(dbUrl, dbUser, dbPass);
                environmentAvailable = true;
                return;
            }
        } catch (Throwable ignored) {
            // Docker unavailable
        }

        environmentAvailable = false;
    }

    private static void runFlywayMigrations(String url, String user, String pass) {
        Flyway flyway = Flyway.configure()
            .dataSource(url, user, pass)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();
    }

    private Connection getConnection() throws Exception {
        if (!environmentAvailable) {
            fail("PostgreSQL catalog assertion test failed closed: An isolated PostgreSQL instance via POSTGRES_TEST_URL "
                + "or a running Docker environment for Testcontainers is mandatory. "
                + "DatabaseIdentityGuard prevents connecting to non-loopback or production hosts.");
        }
        DatabaseIdentityGuard.assertSafeTestDatabase(dbUrl);
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    @Test
    @DisplayName("Assert chk_service_hour_entries_status constrains strictly status column with exact 4 allowed values")
    void assertServiceHourEntriesStatusCheckConstraintInPgCatalog() throws Exception {
        String sql = """
            SELECT 
                c.conname,
                pg_get_constraintdef(c.oid) AS def,
                ARRAY(
                    SELECT a.attname 
                    FROM unnest(c.conkey) k 
                    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k
                ) AS columns
            FROM pg_constraint c
            WHERE c.conrelid = 'service_hour_entries'::regclass 
              AND c.contype = 'c'
            ORDER BY c.conname
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            Map<String, List<String>> constraintColumns = new HashMap<>();
            Map<String, String> constraintDefs = new HashMap<>();

            while (rs.next()) {
                String conname = rs.getString("conname");
                String def = rs.getString("def");
                Array colsArray = rs.getArray("columns");
                String[] cols = (String[]) colsArray.getArray();
                constraintColumns.put(conname, Arrays.asList(cols));
                constraintDefs.put(conname, def);
            }

            // 1. Verify existence of chk_service_hour_entries_status
            assertTrue(constraintColumns.containsKey("chk_service_hour_entries_status"),
                "pg_constraint must contain 'chk_service_hour_entries_status'");

            // 2. Assert strictly one constrained column and that column is 'status'
            List<String> statusCols = constraintColumns.get("chk_service_hour_entries_status");
            assertEquals(1, statusCols.size(), "chk_service_hour_entries_status must bind strictly to 1 column");
            assertEquals("status", statusCols.get(0), "chk_service_hour_entries_status must bind strictly to the 'status' column");

            // 3. Exact AST token set extraction from ARRAY[...]
            String statusDef = constraintDefs.get("chk_service_hour_entries_status");
            assertNotNull(statusDef);
            Set<String> actualStatuses = parseStatusesFromConstraintDef(statusDef);
            assertEquals(EXPECTED_STATUSES, actualStatuses,
                "Allowed status set must be EXACTLY " + EXPECTED_STATUSES + " with no extra or missing values");
            assertEquals(4, actualStatuses.size(),
                "Exactly 4 statuses must be present in chk_service_hour_entries_status definition");

            // 4. Assert service_hour_entries_hours_check binds strictly to 'hours'
            assertTrue(constraintColumns.containsKey("service_hour_entries_hours_check"),
                "pg_constraint must contain 'service_hour_entries_hours_check'");
            List<String> hoursCols = constraintColumns.get("service_hour_entries_hours_check");
            assertEquals(1, hoursCols.size(), "service_hour_entries_hours_check must bind strictly to 1 column");
            assertEquals("hours", hoursCols.get(0), "service_hour_entries_hours_check must bind strictly to the 'hours' column");

            // 5. Assert exactly two CHECK constraints exist on service_hour_entries
            assertEquals(2, constraintColumns.size(), "service_hour_entries must have exactly 2 check constraints");
        }
    }

    @Test
    @DisplayName("Behavioral verification: all 4 allowed statuses succeed; representative invalid values fail with 23514")
    void assertServiceHourEntriesBehavioralStatusAcceptanceAndRejection() throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Setup isolated test entities within rolled-back transaction
                UUID userId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (user_id, name, email, password_hash, status) VALUES (?, 'Schema Test User', ?, '*HASH*', 'ACTIVE')")) {
                    ps.setObject(1, userId);
                    ps.setString(2, "schema-user-" + userId + "@raghunss.edu");
                    ps.executeUpdate();
                }

                UUID volunteerId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO volunteers (volunteer_id, user_id, college_id, department, year_of_study, status) VALUES (?, ?, ?, 'CSE', 2, 'ACTIVE')")) {
                    ps.setObject(1, volunteerId);
                    ps.setObject(2, userId);
                    ps.setString(3, "SC" + userId.toString().substring(0, 7));
                    ps.executeUpdate();
                }

                UUID unitId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nss_units (unit_id, unit_name, unit_number, officer_id) VALUES (?, 'Schema Unit', ?, ?)")) {
                    ps.setObject(1, unitId);
                    ps.setString(2, "SCH-" + userId.toString().substring(0, 8));
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                UUID eventId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO events (event_id, unit_id, created_by, title, event_type, start_at, end_at, venue, capacity, status) " +
                    "VALUES (?, ?, ?, 'Schema Event', 'SERVICE', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 hour', 'Campus', 100, 'OPEN')")) {
                    ps.setObject(1, eventId);
                    ps.setObject(2, unitId);
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                // 1. Prove all 4 intended statuses are accepted
                for (String validStatus : EXPECTED_STATUSES) {
                    UUID entryId = UUID.randomUUID();
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description) " +
                        "VALUES (?, ?, ?, 2.0, ?, 'REGULAR_ACTIVITY', 'Valid status test')")) {
                        ps.setObject(1, entryId);
                        ps.setObject(2, volunteerId);
                        ps.setObject(3, eventId);
                        ps.setString(4, validStatus);
                        int rows = ps.executeUpdate();
                        assertEquals(1, rows, "Insert with valid status '" + validStatus + "' must succeed");
                    }
                    // Delete entry to avoid conflicting with unique partial index on volunteer_id + event_id
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM service_hour_entries WHERE entry_id = ?")) {
                        ps.setObject(1, entryId);
                        ps.executeUpdate();
                    }
                }

                // 2. Prove representative unintended values are rejected with SQLSTATE 23514 (check_violation)
                List<String> rejectedStatuses = List.of(
                    "INVALID",
                    "SUSPENDED",
                    "EXCUSED",
                    "DELETED",
                    "DRAFT",
                    "UNKNOWN",
                    "approved", // case sensitive check
                    ""
                );

                for (String invalidStatus : rejectedStatuses) {
                    UUID entryId = UUID.randomUUID();
                    SQLException ex = assertThrows(SQLException.class, () -> {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description) " +
                            "VALUES (?, ?, ?, 2.0, ?, 'REGULAR_ACTIVITY', 'Invalid status test')")) {
                            ps.setObject(1, entryId);
                            ps.setObject(2, volunteerId);
                            ps.setObject(3, eventId);
                            ps.setString(4, invalidStatus);
                            ps.executeUpdate();
                        }
                    }, "Insert with invalid status '" + invalidStatus + "' must fail check constraint");

                    // PostgreSQL check violation code is 23514
                    assertEquals("23514", ex.getSQLState(),
                        "Failure for status '" + invalidStatus + "' must be check_violation (SQLSTATE 23514)");
                    assertTrue(ex.getMessage().contains("chk_service_hour_entries_status"),
                        "Error message must reference chk_service_hour_entries_status. Found: " + ex.getMessage());
                }

            } finally {
                // Always roll back test transaction cleanly
                conn.rollback();
            }
        }
    }

    public static Set<String> parseStatusesFromConstraintDef(String def) {
        if (def == null) return Collections.emptySet();
        Set<String> statuses = new TreeSet<>();
        Pattern pattern = Pattern.compile("'([^']+)'");
        Matcher matcher = pattern.matcher(def);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!token.contains(" ") && !token.equalsIgnoreCase("text")) {
                statuses.add(token.toUpperCase(Locale.ROOT));
            }
        }
        return statuses;
    }
}
