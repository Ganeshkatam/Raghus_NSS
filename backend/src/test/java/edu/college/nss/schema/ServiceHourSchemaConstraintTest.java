package edu.college.nss.schema;

import edu.college.nss.security.DatabaseIdentityGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.flywaydb.core.Flyway;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Catalog verification asserting the exact structure and column attribution
 * of check constraints on the service_hour_entries table in PostgreSQL.
 *
 * Inspects pg_constraint.conkey joined with pg_attribute.attnum to prove:
 * 1. chk_service_hour_entries_status constrains strictly ['status'] (no other columns).
 * 2. Allowed status set is strictly {'APPROVED', 'PENDING', 'REJECTED', 'REVOKED'}.
 * 3. service_hour_entries_hours_check constrains strictly ['hours'].
 */
@Tag("postgres-concurrency")
@DisplayName("PostgreSQL Catalog Assertion for Service Hour Check Constraints")
public class ServiceHourSchemaConstraintTest {

    private static PostgreSQLContainer<?> postgreSqlContainer;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;
    private static boolean environmentAvailable = false;

    @BeforeAll
    static void initDatabaseEnvironment() {
        String customTestUrl = System.getenv("POSTGRES_TEST_URL");
        if (customTestUrl == null || customTestUrl.isBlank()) {
            customTestUrl = System.getProperty("postgres.test.url");
        }

        if (customTestUrl != null && !customTestUrl.isBlank()) {
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
                + "or a running Docker environment for Testcontainers is mandatory.");
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

            // 3. Assert allowed values are strictly APPROVED, PENDING, REJECTED, REVOKED
            String statusDef = constraintDefs.get("chk_service_hour_entries_status");
            assertNotNull(statusDef);
            String normalizedDef = statusDef.replace(" ", "").toUpperCase(Locale.ROOT);
            assertTrue(normalizedDef.contains("APPROVED"), "Constraint must allow APPROVED");
            assertTrue(normalizedDef.contains("PENDING"), "Constraint must allow PENDING");
            assertTrue(normalizedDef.contains("REJECTED"), "Constraint must allow REJECTED");
            assertTrue(normalizedDef.contains("REVOKED"), "Constraint must allow REVOKED");

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
}
