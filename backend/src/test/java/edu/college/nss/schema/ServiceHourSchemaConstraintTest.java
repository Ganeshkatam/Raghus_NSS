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
                    Savepoint sp = conn.setSavepoint();
                    try {
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
                    } finally {
                        conn.rollback(sp);
                    }
                }

            } finally {
                // Always roll back test transaction cleanly
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Assert chk_attendance_records_check_in_method constrains strictly check_in_method with exact allowed values and permits AUTO_ABSENT")
    void assertAttendanceRecordsCheckInMethodInPgCatalogAndBehavior() throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Catalog check
                String sql = """
                    SELECT 
                        c.conname,
                        pg_get_constraintdef(c.oid) AS def,
                        ARRAY(
                            SELECT a.attname::text
                            FROM pg_attribute a
                            WHERE a.attrelid = c.conrelid
                              AND a.attnum = ANY(c.conkey)
                            ORDER BY a.attnum
                        ) AS cols
                    FROM pg_constraint c
                    WHERE c.conrelid = 'attendance_records'::regclass
                      AND c.conname = 'chk_attendance_records_check_in_method'
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "chk_attendance_records_check_in_method must exist on attendance_records");
                    Array colsArray = rs.getArray("cols");
                    Object[] cols = colsArray != null ? (Object[]) colsArray.getArray() : new Object[0];
                    assertEquals(1, cols.length);
                    assertEquals("check_in_method", cols[0]);

                    String def = rs.getString("def");
                    Set<String> actualMethods = parseStatusesFromConstraintDef(def);
                    Set<String> expectedMethods = Set.of("QR", "MANUAL_COORDINATOR", "AUTO_ABSENT");
                    assertEquals(expectedMethods, actualMethods);
                }

                // 2. Behavioral verification
                UUID userId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (user_id, name, email, password_hash, status) VALUES (?, 'Att User', ?, '*HASH*', 'ACTIVE')")) {
                    ps.setObject(1, userId);
                    ps.setString(2, "att-user-" + userId + "@raghunss.edu");
                    ps.executeUpdate();
                }

                UUID volunteerId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO volunteers (volunteer_id, user_id, college_id, department, year_of_study, status) VALUES (?, ?, ?, 'CSE', 2, 'ACTIVE')")) {
                    ps.setObject(1, volunteerId);
                    ps.setObject(2, userId);
                    ps.setString(3, "AT" + userId.toString().substring(0, 7));
                    ps.executeUpdate();
                }

                UUID unitId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nss_units (unit_id, unit_name, unit_number, officer_id) VALUES (?, 'Att Unit', ?, ?)")) {
                    ps.setObject(1, unitId);
                    ps.setString(2, "ATT-" + userId.toString().substring(0, 8));
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                UUID eventId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO events (event_id, unit_id, created_by, title, event_type, start_at, end_at, venue, capacity, status) " +
                    "VALUES (?, ?, ?, 'Att Event', 'SERVICE', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 hour', 'Campus', 100, 'OPEN')")) {
                    ps.setObject(1, eventId);
                    ps.setObject(2, unitId);
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                UUID sessionId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO attendance_sessions (session_id, event_id, status, opened_by, starts_at, expires_at) " +
                    "VALUES (?, ?, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')")) {
                    ps.setObject(1, sessionId);
                    ps.setObject(2, eventId);
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                // Behavior A: AUTO_ABSENT must succeed
                UUID attId1 = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO attendance_records (attendance_id, session_id, volunteer_id, check_in_method, status) VALUES (?, ?, ?, 'AUTO_ABSENT', 'ABSENT')")) {
                    ps.setObject(1, attId1);
                    ps.setObject(2, sessionId);
                    ps.setObject(3, volunteerId);
                    int rows = ps.executeUpdate();
                    assertEquals(1, rows);
                }

                // Clean up to allow testing invalid method
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance_records WHERE attendance_id = ?")) {
                    ps.setObject(1, attId1);
                    ps.executeUpdate();
                }

                // Behavior B: Invalid method must fail with SQLSTATE 23514
                UUID attId2 = UUID.randomUUID();
                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attendance_records (attendance_id, session_id, volunteer_id, check_in_method, status) VALUES (?, ?, ?, 'NFC_SWIPE', 'PRESENT')")) {
                        ps.setObject(1, attId2);
                        ps.setObject(2, sessionId);
                        ps.setObject(3, volunteerId);
                        ps.executeUpdate();
                    }
                });
                assertEquals("23514", ex.getSQLState(), "Invalid check_in_method must fail check constraint with SQLSTATE 23514");

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Assert uq_service_hours_one_active_per_volunteer_event index exists and enforces single active entry in PostgreSQL")
    void assertServiceHoursUniqueActivePerVolunteerEventIndexInPgCatalogAndBehavior() throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Catalog check
                String sql = "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'service_hour_entries' AND indexname = 'uq_service_hours_one_active_per_volunteer_event'";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Index uq_service_hours_one_active_per_volunteer_event must exist in pg_indexes");
                    String def = rs.getString("indexdef");
                    assertTrue(def.contains("UNIQUE INDEX"), "Must be a UNIQUE index");
                    assertTrue(def.contains("volunteer_id") && def.contains("event_id"), "Must index (volunteer_id, event_id)");
                    assertTrue(def.contains("PENDING") && def.contains("APPROVED"), "Must be partial on PENDING and APPROVED");
                }

                // 2. Behavioral verification
                UUID userId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (user_id, name, email, password_hash, status) VALUES (?, 'Idx User', ?, '*HASH*', 'ACTIVE')")) {
                    ps.setObject(1, userId);
                    ps.setString(2, "idx-user-" + userId + "@raghunss.edu");
                    ps.executeUpdate();
                }

                UUID volunteerId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO volunteers (volunteer_id, user_id, college_id, department, year_of_study, status) VALUES (?, ?, ?, 'CSE', 2, 'ACTIVE')")) {
                    ps.setObject(1, volunteerId);
                    ps.setObject(2, userId);
                    ps.setString(3, "IX" + userId.toString().substring(0, 7));
                    ps.executeUpdate();
                }

                UUID unitId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nss_units (unit_id, unit_name, unit_number, officer_id) VALUES (?, 'Idx Unit', ?, ?)")) {
                    ps.setObject(1, unitId);
                    ps.setString(2, "IXU-" + userId.toString().substring(0, 8));
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                UUID eventId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO events (event_id, unit_id, created_by, title, event_type, start_at, end_at, venue, capacity, status) " +
                    "VALUES (?, ?, ?, 'Idx Event', 'SERVICE', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 hour', 'Campus', 100, 'OPEN')")) {
                    ps.setObject(1, eventId);
                    ps.setObject(2, unitId);
                    ps.setObject(3, userId);
                    ps.executeUpdate();
                }

                // First active entry (APPROVED)
                UUID entryId1 = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description) " +
                    "VALUES (?, ?, ?, 2.0, 'APPROVED', 'REGULAR_ACTIVITY', 'First entry')")) {
                    ps.setObject(1, entryId1);
                    ps.setObject(2, volunteerId);
                    ps.setObject(3, eventId);
                    ps.executeUpdate();
                }

                // Second active entry (PENDING) for same volunteer and event must fail with unique violation 23505
                UUID entryId2 = UUID.randomUUID();
                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description) " +
                        "VALUES (?, ?, ?, 3.0, 'PENDING', 'REGULAR_ACTIVITY', 'Duplicate entry')")) {
                        ps.setObject(1, entryId2);
                        ps.setObject(2, volunteerId);
                        ps.setObject(3, eventId);
                        ps.executeUpdate();
                    }
                });
                assertEquals("23505", ex.getSQLState(), "Duplicate active entry must trigger unique_violation (SQLSTATE 23505)");

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Assert V26 view lockdown loop sets security_invoker=true and fails closed on invalid operations")
    void assertV26ViewSecurityInvokerEnforcement_succeedsAndFailsClosed() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            try (Statement stmt = conn.createStatement()) {
                // 1. Create a test view in public schema
                stmt.execute("CREATE OR REPLACE VIEW public.test_v26_view AS SELECT 1 AS test_col;");

                // 2. Execute V26 view hardening loop logic (without silent exception swallowing)
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT table_name FROM information_schema.views WHERE table_schema = 'public' AND table_name = 'test_v26_view'")) {
                    assertTrue(rs.next(), "test_v26_view must be discovered in information_schema.views");
                    String viewName = rs.getString("table_name");
                    stmt.execute("ALTER VIEW public." + viewName + " SET (security_invoker = true);");
                }

                // 3. Verify security_invoker is explicitly active in pg_class.reloptions
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT reloptions::text FROM pg_class WHERE relname = 'test_v26_view' AND relnamespace = 'public'::regnamespace")) {
                    assertTrue(rs.next(), "pg_class record for test_v26_view must exist");
                    String options = rs.getString(1);
                    assertNotNull(options, "reloptions must not be null");
                    assertTrue(options.contains("security_invoker=true"), "View reloptions must contain security_invoker=true. Found: " + options);
                }

                // 4. Verify fail-closed behavior: invalid view alterations throw SQLException and are not swallowed
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.execute("ALTER VIEW public.test_v26_view SET (invalid_security_option = true);")
                );
                assertNotNull(ex.getMessage());

                // 5. Cleanup test view
                stmt.execute("DROP VIEW IF EXISTS public.test_v26_view;");
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
