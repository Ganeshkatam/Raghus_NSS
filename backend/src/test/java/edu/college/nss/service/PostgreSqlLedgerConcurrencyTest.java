package edu.college.nss.service;

import edu.college.nss.security.DatabaseIdentityGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for database-level invariants on PostgreSQL:
 * 1. Partial unique index on attendance_corrections (exactly 1 PENDING per record).
 * 2. Partial unique index on service_hour_entries (exactly 1 active claim per volunteer/event).
 * 3. Partial unique index on service_hour_entries (exactly 1 APPROVED credit per attendance record).
 * 4. Pessimistic row locking on attendance_records (FOR UPDATE serialization).
 *
 * This test suite is strictly isolated from production:
 * - Annotated with @Tag("postgres-concurrency") (excluded from regular unit test suite).
 * - Enforces DatabaseIdentityGuard to refuse any connection to cloud, production, or remote instances.
 * - Requires either an isolated POSTGRES_TEST_URL or a local Docker environment running Testcontainers.
 */
@Tag("postgres-concurrency")
public class PostgreSqlLedgerConcurrencyTest {

    private static PostgreSQLContainer<?> postgreSqlContainer;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;
    private static boolean environmentAvailable = false;

    private final UUID testRunId = UUID.randomUUID();
    private UUID testUserId;
    private UUID testUnitId;
    private UUID testEventId;
    private UUID testVolunteerId;
    private UUID testSessionId;
    private UUID testAttendanceId;

    @BeforeAll
    static void initDatabaseEnvironment() {
        // 1. Check for explicitly provided isolated test database URL
        String customTestUrl = System.getenv("POSTGRES_TEST_URL");
        if (customTestUrl == null || customTestUrl.isBlank()) {
            customTestUrl = System.getProperty("postgres.test.url");
        }

        if (customTestUrl != null && !customTestUrl.isBlank()) {
            // STRICT SAFETY ENFORCEMENT: Guard must verify this is NOT a production or cloud host
            DatabaseIdentityGuard.assertSafeTestDatabase(customTestUrl);
            dbUrl = customTestUrl;
            dbUser = System.getenv().getOrDefault("POSTGRES_TEST_USERNAME", "postgres");
            dbPass = System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "postgres");
            runFlywayMigrations(dbUrl, dbUser, dbPass);
            environmentAvailable = true;
            return;
        }

        // 2. Check for local Docker availability for Testcontainers
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                postgreSqlContainer = new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("rec_nss_test")
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

    @AfterAll
    static void stopDatabaseEnvironment() {
        if (postgreSqlContainer != null && postgreSqlContainer.isRunning()) {
            postgreSqlContainer.stop();
        }
    }

    private static void runFlywayMigrations(String url, String user, String pass) {
        Flyway flyway = Flyway.configure()
            .dataSource(url, user, pass)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!environmentAvailable) {
            fail("PostgreSQL concurrency test suite failed closed: An isolated PostgreSQL instance via POSTGRES_TEST_URL "
                + "or a running Docker environment for Testcontainers is mandatory. "
                + "Connecting to remote/production databases is strictly prohibited by DatabaseIdentityGuard.");
        }

        setupTestData();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (environmentAvailable) {
            cleanupTestData();
        }
    }

    private Connection getPostgresConnection() throws Exception {
        DatabaseIdentityGuard.assertSafeTestDatabase(dbUrl);
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    private void setupTestData() throws Exception {
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);

            // 1. Create test user
            testUserId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (user_id, name, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')")) {
                ps.setObject(1, testUserId);
                ps.setString(2, "Concurrency Test User");
                ps.setString(3, "concurrency-" + testRunId + "@raghunss.edu");
                ps.setString(4, "*HASH*");
                ps.executeUpdate();
            }

            // 2. Create test NSS unit
            testUnitId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO nss_units (unit_id, unit_name, unit_number, officer_id) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, testUnitId);
                ps.setString(2, "Concurrency Unit");
                ps.setString(3, "CONC-" + testRunId.toString().substring(0, 8));
                ps.setObject(4, testUserId);
                ps.executeUpdate();
            }

            // 3. Create test volunteer
            testVolunteerId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO volunteers (volunteer_id, user_id, college_id, department, year_of_study, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')")) {
                ps.setObject(1, testVolunteerId);
                ps.setObject(2, testUserId);
                ps.setString(3, "C" + testRunId.toString().substring(0, 7));
                ps.setString(4, "CSE");
                ps.setInt(5, 3);
                ps.executeUpdate();
            }

            // 4. Create test event
            testEventId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO events (event_id, unit_id, created_by, title, event_type, start_at, end_at, venue, capacity, status) " +
                "VALUES (?, ?, ?, ?, 'SERVICE', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 hour', 'Campus', 100, 'OPEN')")) {
                ps.setObject(1, testEventId);
                ps.setObject(2, testUnitId);
                ps.setObject(3, testUserId);
                ps.setString(4, "Concurrency Event");
                ps.executeUpdate();
            }

            // 5. Create test session
            testSessionId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO attendance_sessions (session_id, event_id, opened_by, starts_at, expires_at, status, qr_secret) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP + INTERVAL '30 minutes', 'OPEN', 'sec')")) {
                ps.setObject(1, testSessionId);
                ps.setObject(2, testEventId);
                ps.setObject(3, testUserId);
                ps.executeUpdate();
            }

            // 6. Create test attendance record
            testAttendanceId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO attendance_records (attendance_id, session_id, volunteer_id, check_in_method, status) " +
                "VALUES (?, ?, ?, 'QR', 'PRESENT')")) {
                ps.setObject(1, testAttendanceId);
                ps.setObject(2, testSessionId);
                ps.setObject(3, testVolunteerId);
                ps.executeUpdate();
            }

            conn.commit();
        }
    }

    private void cleanupTestData() throws Exception {
        if (testUserId == null) return;
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                if (testVolunteerId != null) {
                    stmt.executeUpdate("DELETE FROM service_hour_entries WHERE volunteer_id = '" + testVolunteerId + "'");
                    stmt.executeUpdate("DELETE FROM unit_memberships WHERE volunteer_id = '" + testVolunteerId + "'");
                }
                if (testAttendanceId != null) {
                    stmt.executeUpdate("DELETE FROM attendance_corrections WHERE attendance_id = '" + testAttendanceId + "'");
                    stmt.executeUpdate("DELETE FROM attendance_records WHERE attendance_id = '" + testAttendanceId + "'");
                }
                if (testEventId != null) {
                    stmt.executeUpdate("DELETE FROM attendance_records WHERE session_id IN (SELECT session_id FROM attendance_sessions WHERE event_id = '" + testEventId + "')");
                    stmt.executeUpdate("DELETE FROM attendance_sessions WHERE event_id = '" + testEventId + "'");
                    stmt.executeUpdate("DELETE FROM events WHERE event_id = '" + testEventId + "'");
                }
                if (testVolunteerId != null) {
                    stmt.executeUpdate("DELETE FROM volunteers WHERE volunteer_id = '" + testVolunteerId + "'");
                }
                if (testUnitId != null) {
                    stmt.executeUpdate("DELETE FROM unit_memberships WHERE unit_id = '" + testUnitId + "'");
                    stmt.executeUpdate("DELETE FROM nss_units WHERE unit_id = '" + testUnitId + "'");
                }
                stmt.executeUpdate("DELETE FROM users WHERE user_id = '" + testUserId + "'");
            }
            conn.commit();
        }
    }

    @Test
    void concurrentCorrectionCreation_exactlyOnePendingAllowed_andOthersFailClosed() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateErrorCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    try (Connection conn = getPostgresConnection()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO attendance_corrections (correction_id, attendance_id, corrected_by, previous_status, new_status, reason, status) " +
                            "VALUES (?, ?, ?, 'PRESENT', 'EXCUSED', ?, 'PENDING')")) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, testAttendanceId);
                            ps.setObject(3, testUserId);
                            ps.setString(4, "Concurrent correction attempt " + threadNum);
                            ps.executeUpdate();
                            successCount.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        if (ex.getMessage().contains("uq_att_corrections_one_pending_per_record") ||
                            ex.getMessage().contains("duplicate key value")) {
                            duplicateErrorCount.incrementAndGet();
                        } else {
                            ex.printStackTrace();
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in creating a PENDING correction");
            assertEquals(threadCount - 1, duplicateErrorCount.get(), "All other concurrent threads must fail closed on unique constraint");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM attendance_corrections WHERE attendance_id = ? AND status = 'PENDING'")) {
                ps.setObject(1, testAttendanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Database must contain exactly 1 PENDING correction");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentActiveClaimSubmission_exactlyOneClaimAllowed_andOthersFailClosed() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateErrorCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    try (Connection conn = getPostgresConnection()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description, attendance_id) " +
                            "VALUES (?, ?, ?, 3.00, 'PENDING', 'REGULAR_ACTIVITY', ?, NULL)")) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, testVolunteerId);
                            ps.setObject(3, testEventId);
                            ps.setString(4, "Concurrent claim attempt " + threadNum);
                            ps.executeUpdate();
                            successCount.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        if (ex.getMessage().contains("uq_service_hours_one_active_event_claim") ||
                            ex.getMessage().contains("duplicate key value")) {
                            duplicateErrorCount.incrementAndGet();
                        } else {
                            ex.printStackTrace();
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in submitting active claim");
            assertEquals(threadCount - 1, duplicateErrorCount.get(), "All other concurrent submissions must fail closed on unique constraint");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM service_hour_entries WHERE volunteer_id = ? AND event_id = ? AND status IN ('PENDING', 'APPROVED') AND attendance_id IS NULL")) {
                ps.setObject(1, testVolunteerId);
                ps.setObject(2, testEventId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Database must contain exactly 1 active claim for volunteer and event");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentApprovedAttendanceCredit_exactlyOneApprovedEntryAllowed_andOthersFailClosed() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateErrorCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    try (Connection conn = getPostgresConnection()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, attendance_id, hours, status, category, description) " +
                            "VALUES (?, ?, ?, ?, 2.00, 'APPROVED', 'REGULAR_ACTIVITY', ?)")) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, testVolunteerId);
                            ps.setObject(3, testEventId);
                            ps.setObject(4, testAttendanceId);
                            ps.setString(5, "Concurrent credit attempt " + threadNum);
                            ps.executeUpdate();
                            successCount.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        if (ex.getMessage().contains("uq_service_hours_one_approved_per_attendance") ||
                            ex.getMessage().contains("duplicate key value")) {
                            duplicateErrorCount.incrementAndGet();
                        } else {
                            ex.printStackTrace();
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in creating APPROVED credit for attendance");
            assertEquals(threadCount - 1, duplicateErrorCount.get(), "All other concurrent attempts must fail closed on unique constraint");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM service_hour_entries WHERE attendance_id = ? AND status = 'APPROVED'")) {
                ps.setObject(1, testAttendanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Database must contain exactly 1 APPROVED entry for attendance");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentPessimisticRowLocking_serializesManualDemotionAndCorrectionReview() throws Exception {
        CountDownLatch threadALockedLatch = new CountDownLatch(1);
        CountDownLatch threadBAttemptingLatch = new CountDownLatch(1);
        CountDownLatch threadADoneLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> futureA = executor.submit(() -> {
                try (Connection conn = getPostgresConnection()) {
                    conn.setAutoCommit(false);

                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT attendance_id, status FROM attendance_records WHERE attendance_id = ? FOR UPDATE")) {
                        ps.setObject(1, testAttendanceId);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                            assertEquals("PRESENT", rs.getString("status"));
                        }
                    }

                    threadALockedLatch.countDown();
                    threadBAttemptingLatch.await(5, TimeUnit.SECONDS);

                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE attendance_records SET status = 'ABSENT' WHERE attendance_id = ?")) {
                        ps.setObject(1, testAttendanceId);
                        ps.executeUpdate();
                    }

                    Thread.sleep(800);
                    conn.commit();
                    threadADoneLatch.countDown();
                }
                return null;
            });

            Future<String> futureB = executor.submit(() -> {
                threadALockedLatch.await(5, TimeUnit.SECONDS);
                threadBAttemptingLatch.countDown();

                long startTime = System.currentTimeMillis();
                String observedStatus;

                try (Connection conn = getPostgresConnection()) {
                    conn.setAutoCommit(false);

                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT attendance_id, status FROM attendance_records WHERE attendance_id = ? FOR UPDATE")) {
                        ps.setObject(1, testAttendanceId);
                        try (ResultSet rs = ps.executeQuery()) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            assertTrue(elapsed >= 500, "Thread B must have blocked waiting for Thread A to release lock (elapsed: " + elapsed + "ms)");
                            assertTrue(rs.next());
                            observedStatus = rs.getString("status");
                        }
                    }
                    conn.commit();
                }
                return observedStatus;
            });

            futureA.get(15, TimeUnit.SECONDS);
            String statusSeenByThreadB = futureB.get(15, TimeUnit.SECONDS);

            assertEquals("ABSENT", statusSeenByThreadB, "Thread B must observe committed status from Thread A after lock release");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentQrAttendanceCreditAndManualClaim_inPostgreSql_leavesExactlyOneActiveEntry() throws Exception {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger uniqueViolationCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            // 3 threads attempt approved attendance credit; 3 threads attempt pending manual claims
            for (int i = 0; i < threadCount; i++) {
                final boolean isApprovedCredit = (i % 2 == 0);
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    try (Connection conn = getPostgresConnection()) {
                        conn.setAutoCommit(false);
                        try {
                            String sql = isApprovedCredit
                                ? "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description, attendance_id) " +
                                  "VALUES (?, ?, ?, 2.0, 'APPROVED', 'REGULAR_ACTIVITY', 'QR attendance credit " + idx + "', ?)"
                                : "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description, attendance_id) " +
                                  "VALUES (?, ?, ?, 2.0, 'PENDING', 'REGULAR_ACTIVITY', 'Manual claim " + idx + "', NULL)";
                            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                ps.setObject(1, UUID.randomUUID());
                                ps.setObject(2, testVolunteerId);
                                ps.setObject(3, testEventId);
                                if (isApprovedCredit) {
                                    ps.setObject(4, testAttendanceId);
                                }
                                ps.executeUpdate();
                                conn.commit();
                                successCount.incrementAndGet();
                            }
                        } catch (Exception ex) {
                            conn.rollback();
                            if (ex.getMessage().contains("uq_service_hours_one_active_per_volunteer_event") ||
                                ex.getMessage().contains("duplicate key") ||
                                ex.getMessage().contains("23505")) {
                                uniqueViolationCount.incrementAndGet();
                            } else {
                                throw ex;
                            }
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in creating an active entry");
            assertEquals(threadCount - 1, uniqueViolationCount.get(), "All other concurrent threads must fail closed on unique constraint");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM service_hour_entries WHERE volunteer_id = ? AND event_id = ? AND status IN ('PENDING', 'APPROVED')")) {
                ps.setObject(1, testVolunteerId);
                ps.setObject(2, testEventId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Database must contain exactly 1 active service hour entry");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentAttendanceSessionCreation_againstUniqueIndex_leavesExactlyOneOpenSession() throws Exception {
        // Clear any existing session for testEventId
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM attendance_records WHERE session_id IN (SELECT session_id FROM attendance_sessions WHERE event_id = '" + testEventId + "')");
                stmt.executeUpdate("DELETE FROM attendance_sessions WHERE event_id = '" + testEventId + "'");
            }
            conn.commit();
        }

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger uniqueViolationCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    try (Connection conn = getPostgresConnection()) {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO attendance_sessions (session_id, event_id, opened_by, starts_at, expires_at, status, qr_secret) " +
                            "VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '10 minutes', CURRENT_TIMESTAMP + INTERVAL '50 minutes', 'OPEN', ?)")) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, testEventId);
                            ps.setObject(3, testUserId);
                            ps.setString(4, "secret-" + idx);
                            ps.executeUpdate();
                            conn.commit();
                            successCount.incrementAndGet();
                        } catch (Exception ex) {
                            conn.rollback();
                            if (ex.getMessage().contains("uq_one_open_session_per_event") ||
                                ex.getMessage().contains("duplicate key") ||
                                ex.getMessage().contains("23505")) {
                                uniqueViolationCount.incrementAndGet();
                            } else {
                                throw ex;
                            }
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in creating an OPEN session");
            assertEquals(threadCount - 1, uniqueViolationCount.get(), "All other concurrent creations must fail closed on unique index");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM attendance_sessions WHERE event_id = ? AND status = 'OPEN'")) {
                ps.setObject(1, testEventId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Database must contain exactly 1 OPEN session for the event");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentUnitTransfers_sameVolunteer_leavesExactlyOneActiveMembership() throws Exception {
        UUID unitA = testUnitId;
        UUID unitB = UUID.randomUUID();

        // Create unit B
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO nss_units (unit_id, unit_name, unit_number, officer_id) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, unitB);
                ps.setString(2, "Concurrency Unit B");
                ps.setString(3, "CONC-B-" + testRunId.toString().substring(0, 8));
                ps.setObject(4, testUserId);
                ps.executeUpdate();
            }
            // Clear any existing memberships for testVolunteerId
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM unit_memberships WHERE volunteer_id = ?")) {
                ps.setObject(1, testVolunteerId);
                ps.executeUpdate();
            }
            conn.commit();
        }

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger uniqueViolationCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final UUID targetUnit = (i % 2 == 0) ? unitA : unitB;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    try (Connection conn = getPostgresConnection()) {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO unit_memberships (membership_id, volunteer_id, unit_id, is_active) VALUES (?, ?, ?, true)")) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, testVolunteerId);
                            ps.setObject(3, targetUnit);
                            ps.executeUpdate();
                            conn.commit();
                            successCount.incrementAndGet();
                        } catch (Exception ex) {
                            conn.rollback();
                            if (ex.getMessage().contains("uq_active_unit_membership_per_volunteer") ||
                                ex.getMessage().contains("duplicate key") ||
                                ex.getMessage().contains("23505")) {
                                uniqueViolationCount.incrementAndGet();
                            } else {
                                throw ex;
                            }
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in setting active membership");
            assertEquals(threadCount - 1, uniqueViolationCount.get(), "All other concurrent transfers must fail on uq_active_unit_membership_per_volunteer");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM unit_memberships WHERE volunteer_id = ? AND is_active = true")) {
                ps.setObject(1, testVolunteerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Database must contain exactly 1 active unit membership for volunteer");
                }
            }
        } finally {
            try (Connection conn = getPostgresConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM unit_memberships WHERE unit_id = '" + unitB + "'");
                    stmt.executeUpdate("DELETE FROM nss_units WHERE unit_id = '" + unitB + "'");
                }
                conn.commit();
            }
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentUnitCapacityAssignment_underPostgresRowLocking_strictlyEnforcesCapacity() throws Exception {
        UUID cappedUnitId = UUID.randomUUID();
        int maxCapacity = 2;

        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO nss_units (unit_id, unit_name, unit_number, officer_id, capacity) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, cappedUnitId);
                ps.setString(2, "Capped Concurrency Unit");
                ps.setString(3, "CAP-" + testRunId.toString().substring(0, 8));
                ps.setObject(4, testUserId);
                ps.setInt(5, maxCapacity);
                ps.executeUpdate();
            }
            conn.commit();
        }

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger capacityExceededCount = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    UUID uId = UUID.randomUUID();
                    UUID vId = UUID.randomUUID();

                    try (Connection conn = getPostgresConnection()) {
                        conn.setAutoCommit(false);

                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO users (user_id, name, email, password_hash, status) VALUES (?, ?, ?, '*', 'ACTIVE')")) {
                            ps.setObject(1, uId);
                            ps.setString(2, "Member " + idx);
                            ps.setString(3, "m" + idx + "-" + testRunId + "@raghunss.edu");
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO volunteers (volunteer_id, user_id, college_id, department, year_of_study, status) VALUES (?, ?, ?, 'CSE', 2, 'ACTIVE')")) {
                            ps.setObject(1, vId);
                            ps.setObject(2, uId);
                            ps.setString(3, "CL" + idx + "-" + testRunId.toString().substring(0, 4));
                            ps.executeUpdate();
                        }

                        // Acquire row lock FOR UPDATE on unit
                        int unitCapacity;
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT unit_id, capacity FROM nss_units WHERE unit_id = ? FOR UPDATE")) {
                            ps.setObject(1, cappedUnitId);
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next());
                                unitCapacity = rs.getInt("capacity");
                            }
                        }

                        int currentCount;
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM unit_memberships WHERE unit_id = ? AND is_active = true")) {
                            ps.setObject(1, cappedUnitId);
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next());
                                currentCount = rs.getInt(1);
                            }
                        }

                        if (currentCount < unitCapacity) {
                            try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO unit_memberships (membership_id, volunteer_id, unit_id, is_active) VALUES (?, ?, ?, true)")) {
                                ps.setObject(1, UUID.randomUUID());
                                ps.setObject(2, vId);
                                ps.setObject(3, cappedUnitId);
                                ps.executeUpdate();
                            }
                            conn.commit();
                            successCount.incrementAndGet();
                        } else {
                            conn.rollback();
                            capacityExceededCount.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(2, successCount.get(), "Exactly 2 members should be admitted under FOR UPDATE serialization");
            assertEquals(4, capacityExceededCount.get(), "Other 4 concurrent threads must observe capacity reached");

            try (Connection conn = getPostgresConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM unit_memberships WHERE unit_id = ? AND is_active = true")) {
                ps.setObject(1, cappedUnitId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1), "Database must contain exactly 2 active memberships");
                }
            }
        } finally {
            try (Connection conn = getPostgresConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM unit_memberships WHERE unit_id = '" + cappedUnitId + "'");
                    stmt.executeUpdate("DELETE FROM nss_units WHERE unit_id = '" + cappedUnitId + "'");
                }
                conn.commit();
            }
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentLockOrder_attendanceRecordBeforeServiceHourEntry_eliminatesDeadlocks() throws Exception {
        UUID entryId = UUID.randomUUID();

        // Create pending claim linked to testAttendanceId
        try (Connection conn = getPostgresConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO service_hour_entries (entry_id, volunteer_id, event_id, hours, status, category, description, attendance_id) " +
                "VALUES (?, ?, ?, 2.0, 'PENDING', 'REGULAR_ACTIVITY', 'Pending race claim', ?)")) {
                ps.setObject(1, entryId);
                ps.setObject(2, testVolunteerId);
                ps.setObject(3, testEventId);
                ps.setObject(4, testAttendanceId);
                ps.executeUpdate();
            }
            conn.commit();
        }

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger deadlockCount = new AtomicInteger(0);

            // Thread 1: Simulating Approval (AttendanceRecord -> ServiceHourEntry)
            Future<String> approvalFuture = executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                try (Connection conn = getPostgresConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        // 1. Lock AttendanceRecord FIRST
                        String attStatus;
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT attendance_id, status FROM attendance_records WHERE attendance_id = ? FOR UPDATE")) {
                            ps.setObject(1, testAttendanceId);
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next());
                                attStatus = rs.getString("status");
                            }
                        }

                        // 2. Lock ServiceHourEntry SECOND
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT entry_id, status FROM service_hour_entries WHERE entry_id = ? FOR UPDATE")) {
                            ps.setObject(1, entryId);
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next());
                            }
                        }

                        if ("PRESENT".equalsIgnoreCase(attStatus)) {
                            try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE service_hour_entries SET status = 'APPROVED' WHERE entry_id = ?")) {
                                ps.setObject(1, entryId);
                                ps.executeUpdate();
                            }
                            conn.commit();
                            return "APPROVED";
                        } else {
                            conn.rollback();
                            return "REJECTED_BY_INVARIANT";
                        }
                    } catch (SQLException ex) {
                        conn.rollback();
                        if ("40P01".equals(ex.getSQLState())) {
                            deadlockCount.incrementAndGet();
                        }
                        throw ex;
                    }
                }
            });

            // Thread 2: Simulating Demotion (AttendanceRecord -> ServiceHourEntry)
            Future<String> demotionFuture = executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                try (Connection conn = getPostgresConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        // 1. Lock AttendanceRecord FIRST
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT attendance_id, status FROM attendance_records WHERE attendance_id = ? FOR UPDATE")) {
                            ps.setObject(1, testAttendanceId);
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next());
                            }
                        }

                        try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE attendance_records SET status = 'ABSENT' WHERE attendance_id = ?")) {
                            ps.setObject(1, testAttendanceId);
                            ps.executeUpdate();
                        }

                        // 2. Lock ServiceHourEntry SECOND
                        try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT entry_id, status FROM service_hour_entries WHERE attendance_id = ? FOR UPDATE")) {
                            ps.setObject(1, testAttendanceId);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    if ("APPROVED".equals(rs.getString("status"))) {
                                        try (PreparedStatement psRevoke = conn.prepareStatement(
                                            "UPDATE service_hour_entries SET status = 'REVOKED' WHERE entry_id = ?")) {
                                            psRevoke.setObject(1, rs.getObject("entry_id"));
                                            psRevoke.executeUpdate();
                                        }
                                    }
                                }
                            }
                        }

                        conn.commit();
                        return "DEMOTED_ABSENT";
                    } catch (SQLException ex) {
                        conn.rollback();
                        if ("40P01".equals(ex.getSQLState())) {
                            deadlockCount.incrementAndGet();
                        }
                        throw ex;
                    }
                }
            });

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            String approvalResult = approvalFuture.get(15, TimeUnit.SECONDS);
            String demotionResult = demotionFuture.get(15, TimeUnit.SECONDS);

            assertEquals(0, deadlockCount.get(), "Consistent lock ordering (Attendance -> Entry) must guarantee zero deadlocks (40P01)");
            assertEquals("DEMOTED_ABSENT", demotionResult);
            assertTrue("APPROVED".equals(approvalResult) || "REJECTED_BY_INVARIANT".equals(approvalResult));

            // Verify final DB state
            try (Connection conn = getPostgresConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM attendance_records WHERE attendance_id = ?")) {
                    ps.setObject(1, testAttendanceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("ABSENT", rs.getString("status"));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM service_hour_entries WHERE entry_id = ?")) {
                    ps.setObject(1, entryId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertNotEquals("APPROVED", rs.getString("status"));
                    }
                }
            }
        } finally {
            try (Connection conn = getPostgresConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM service_hour_entries WHERE entry_id = '" + entryId + "'");
                }
                conn.commit();
            }
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
