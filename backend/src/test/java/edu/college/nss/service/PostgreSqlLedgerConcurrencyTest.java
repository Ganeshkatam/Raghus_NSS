package edu.college.nss.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PostgreSqlLedgerConcurrencyTest {

    private String url;
    private String user;
    private String pass;
    private boolean isPostgreSqlAvailable = false;

    private final UUID testRunId = UUID.randomUUID();
    private UUID testUserId;
    private UUID testUnitId;
    private UUID testEventId;
    private UUID testVolunteerId;
    private UUID testSessionId;
    private UUID testAttendanceId;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> env = loadEnvFile();
        url = env.getOrDefault("SPRING_DATASOURCE_URL", System.getenv("SPRING_DATASOURCE_URL"));
        user = env.getOrDefault("SPRING_DATASOURCE_USERNAME", System.getenv("SPRING_DATASOURCE_USERNAME"));
        pass = env.getOrDefault("SPRING_DATASOURCE_PASSWORD", System.getenv("SPRING_DATASOURCE_PASSWORD"));

        if (url != null && user != null && pass != null && !pass.isBlank() && !"your_supabase_db_password".equals(pass)) {
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                isPostgreSqlAvailable = true;
            } catch (Exception e) {
                isPostgreSqlAvailable = false;
            }
        }

        if (!isPostgreSqlAvailable) {
            fail("PostgreSQL concurrency test suite failed: PostgreSQL credentials are not configured or connection failed. Live or isolated PostgreSQL is mandatory for this test suite.");
        }

        setupTestData();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (isPostgreSqlAvailable) {
            cleanupTestData();
        }
    }

    private Connection getPostgresConnection() throws Exception {
        return DriverManager.getConnection(url, user, pass);
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
                }
                if (testAttendanceId != null) {
                    stmt.executeUpdate("DELETE FROM attendance_corrections WHERE attendance_id = '" + testAttendanceId + "'");
                    stmt.executeUpdate("DELETE FROM attendance_records WHERE attendance_id = '" + testAttendanceId + "'");
                }
                if (testSessionId != null) {
                    stmt.executeUpdate("DELETE FROM attendance_sessions WHERE session_id = '" + testSessionId + "'");
                }
                if (testEventId != null) {
                    stmt.executeUpdate("DELETE FROM events WHERE event_id = '" + testEventId + "'");
                }
                if (testVolunteerId != null) {
                    stmt.executeUpdate("DELETE FROM volunteers WHERE volunteer_id = '" + testVolunteerId + "'");
                }
                if (testUnitId != null) {
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

            // INVARIANT VERIFICATION: Exactly ONE insert succeeded, and 9 were rejected by PostgreSQL partial index
            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in creating a PENDING correction");
            assertEquals(threadCount - 1, duplicateErrorCount.get(), "All other concurrent threads must fail closed on unique constraint");

            // Verify count directly in PostgreSQL
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

            // INVARIANT VERIFICATION: Exactly ONE claim succeeded, 9 failed closed on uq_service_hours_one_active_event_claim
            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in submitting active claim");
            assertEquals(threadCount - 1, duplicateErrorCount.get(), "All other concurrent submissions must fail closed on unique constraint");

            // Verify database state
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

            // INVARIANT VERIFICATION: Exactly ONE credit succeeded, 9 failed closed on uq_service_hours_one_approved_per_attendance
            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in creating APPROVED credit for attendance");
            assertEquals(threadCount - 1, duplicateErrorCount.get(), "All other concurrent attempts must fail closed on unique constraint");

            // Verify database state
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
            // Thread A: Acquires row-level lock on attendance_records, updates status to ABSENT, holds lock for 800ms
            Future<Void> futureA = executor.submit(() -> {
                try (Connection conn = getPostgresConnection()) {
                    conn.setAutoCommit(false);

                    // Lock row
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

                    // Update row while holding lock
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

            // Thread B: Attempts to lock the same row with FOR UPDATE after Thread A has acquired the lock
            Future<String> futureB = executor.submit(() -> {
                threadALockedLatch.await(5, TimeUnit.SECONDS);
                threadBAttemptingLatch.countDown();

                long startTime = System.currentTimeMillis();
                String observedStatus;

                try (Connection conn = getPostgresConnection()) {
                    conn.setAutoCommit(false);

                    // This query must block until Thread A commits!
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

            // INVARIANT VERIFICATION: Thread B serialized cleanly behind Thread A and observed committed status ABSENT
            assertEquals("ABSENT", statusSeenByThreadB, "Thread B must observe committed status from Thread A after lock release");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Map<String, String> loadEnvFile() {
        Map<String, String> map = new HashMap<>();
        File envFile = new File("../.env");
        if (!envFile.exists()) {
            envFile = new File(".env");
        }
        if (envFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(envFile.toPath());
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        int idx = trimmed.indexOf('=');
                        map.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return map;
    }
}
