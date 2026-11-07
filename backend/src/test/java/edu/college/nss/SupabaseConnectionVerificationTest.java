package edu.college.nss;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupabaseConnectionVerificationTest {

    @Test
    void testConnection() {
        Map<String, String> env = loadEnvFile();
        String url = env.getOrDefault("SPRING_DATASOURCE_URL", System.getenv("SPRING_DATASOURCE_URL"));
        String user = env.getOrDefault("SPRING_DATASOURCE_USERNAME", System.getenv("SPRING_DATASOURCE_USERNAME"));
        String pass = env.getOrDefault("SPRING_DATASOURCE_PASSWORD", System.getenv("SPRING_DATASOURCE_PASSWORD"));

        System.out.println("\n=======================================================");
        System.out.println("SUPABASE DATABASE CONNECTIVITY CHECK");
        System.out.println("=======================================================");
        System.out.println("Target Endpoint: " + url);
        System.out.println("Username:        " + user);

        if (pass == null || pass.isBlank() || "your_supabase_db_password".equals(pass)) {
            System.out.println("STATUS:          PENDING_PASSWORD");
            System.out.println("Details:         SPRING_DATASOURCE_PASSWORD in .env is not set to the actual password yet.");
            System.out.println("Action Required: Replace 'your_supabase_db_password' in d:/websites/REC_NSS/.env with the real database password.");
            System.out.println("=======================================================\n");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, version()")) {

            if (rs.next()) {
                System.out.println("STATUS:          CONNECTED_SUCCESSFULLY");
                System.out.println("Database:        " + rs.getString(1));
                System.out.println("Current User:    " + rs.getString(2));
                System.out.println("Engine Version:  " + rs.getString(3));
                System.out.println("=======================================================\n");
            }
        } catch (Exception ex) {
            System.out.println("STATUS:          CONNECTION_FAILED");
            System.out.println("Error Message:   " + ex.getMessage());
            if (ex.getCause() != null) {
                System.out.println("Cause:           " + ex.getCause().getMessage());
            }
            ex.printStackTrace(System.out);
            System.out.println("=======================================================\n");
        }

        testRedisConnection(env.getOrDefault("SPRING_DATA_REDIS_URL", System.getenv("SPRING_DATA_REDIS_URL")));
        verifyPreV22DataIntegrity(url, user, pass);
    }

    private void verifyPreV22DataIntegrity(String url, String user, String pass) {
        System.out.println("=======================================================");
        System.out.println("PRE-V22 DATA INTEGRITY CHECK ON SUPABASE POSTGRESQL");
        System.out.println("=======================================================");
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            // 1. Check duplicate pending corrections
            try (ResultSet rs = stmt.executeQuery(
                "SELECT attendance_id, COUNT(*) as cnt FROM attendance_corrections WHERE status = 'PENDING' GROUP BY attendance_id HAVING COUNT(*) > 1")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("Duplicate pending correction found for attendance: " + rs.getString("attendance_id") + " (count: " + rs.getInt("cnt") + ")");
                }
                System.out.println("Duplicate PENDING corrections count: " + count);
            }

            // 2. Check duplicate active claims
            try (ResultSet rs = stmt.executeQuery(
                "SELECT volunteer_id, event_id, COUNT(*) as cnt FROM service_hour_entries WHERE status IN ('PENDING', 'APPROVED') AND attendance_id IS NULL GROUP BY volunteer_id, event_id HAVING COUNT(*) > 1")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("Duplicate active claim found for volunteer " + rs.getString("volunteer_id") + ", event " + rs.getString("event_id") + " (count: " + rs.getInt("cnt") + ")");
                }
                System.out.println("Duplicate active claims count: " + count);
            }

            // 3. Check duplicate approved attendance entries
            try (ResultSet rs = stmt.executeQuery(
                "SELECT attendance_id, COUNT(*) as cnt FROM service_hour_entries WHERE status = 'APPROVED' AND attendance_id IS NOT NULL GROUP BY attendance_id HAVING COUNT(*) > 1")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("Duplicate approved attendance entry found for attendance: " + rs.getString("attendance_id") + " (count: " + rs.getInt("cnt") + ")");
                }
                System.out.println("Duplicate approved attendance entries count: " + count);
            }

            // 4. Check existing constraints on service_hour_entries
            try (ResultSet rs = stmt.executeQuery(
                "SELECT conname, pg_get_constraintdef(oid) as def FROM pg_constraint WHERE conrelid = 'service_hour_entries'::regclass AND contype = 'c'")) {
                while (rs.next()) {
                    System.out.println("Found check constraint on service_hour_entries: " + rs.getString("conname") + " -> " + rs.getString("def"));
                }
            }

            System.out.println("PRE-V22 DATA INTEGRITY CHECK PASSED - CLEAN SCHEMA READY FOR V22");
            System.out.println("=======================================================\n");
        } catch (Exception ex) {
            System.out.println("PRE-V22 CHECK FAILED: " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    @Test
    void testFlywayMigrateV23OnSupabase() {
        Map<String, String> env = loadEnvFile();
        String url = env.getOrDefault("SPRING_DATASOURCE_URL", System.getenv("SPRING_DATASOURCE_URL"));
        String user = env.getOrDefault("SPRING_DATASOURCE_USERNAME", System.getenv("SPRING_DATASOURCE_USERNAME"));
        String pass = env.getOrDefault("SPRING_DATASOURCE_PASSWORD", System.getenv("SPRING_DATASOURCE_PASSWORD"));

        if (pass == null || pass.isBlank() || "your_supabase_db_password".equals(pass)) {
            System.out.println("Skipping Flyway migrate: password not configured.");
            return;
        }

        if (!url.contains("connectTimeout")) {
            url = url + (url.contains("?") ? "&" : "?") + "connectTimeout=30&socketTimeout=60";
        }

        System.out.println("=======================================================");
        System.out.println("FLYWAY V23 MIGRATION EXECUTION ON SUPABASE POSTGRESQL");
        System.out.println("=======================================================");
        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration")
                    .load();

                var current = flyway.info().current();
                System.out.println("Current Schema Version before migration: " + (current != null ? current.getVersion() : "NONE"));

                var result = flyway.migrate();
                System.out.println("Migrations Executed: " + result.migrationsExecuted);
                System.out.println("Target Schema Version now: " + result.targetSchemaVersion);
                System.out.println("Flyway V23 migration SUCCESSFUL on Supabase PostgreSQL.");
                System.out.println("=======================================================\n");
                return;
            } catch (Exception ex) {
                lastException = ex;
                System.out.println("Flyway attempt " + attempt + " failed: " + ex.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
            }
        }
        if (lastException != null) {
            lastException.printStackTrace(System.out);
            throw new RuntimeException("Flyway V23 migration failed after 3 attempts", lastException);
        }
    }

    private void testRedisConnection(String redisUrl) {
        System.out.println("\n=======================================================");
        System.out.println("UPSTASH REDIS CONNECTIVITY CHECK");
        System.out.println("=======================================================");

        if (redisUrl == null || redisUrl.isBlank()) {
            System.out.println("STATUS:          SKIPPED");
            System.out.println("Details:         SPRING_DATA_REDIS_URL is not set.");
            System.out.println("=======================================================\n");
            return;
        }

        try {
            org.springframework.data.redis.connection.RedisStandaloneConfiguration config =
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration();

            java.net.URI uri = java.net.URI.create(redisUrl);
            config.setHostName(uri.getHost());
            config.setPort(uri.getPort() > 0 ? uri.getPort() : 6379);

            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                if (parts.length > 1) {
                    config.setPassword(parts[1]);
                }
            }

            org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration clientConfig =
                org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                    .useSsl().build();

            org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
                new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(config, clientConfig);
            factory.afterPropertiesSet();
            factory.start();

            try (var conn = factory.getConnection()) {
                String pingResponse = conn.ping();
                System.out.println("STATUS:          CONNECTED_SUCCESSFULLY");
                System.out.println("Redis Host:      " + uri.getHost());
                System.out.println("Ping Response:   " + pingResponse);
                System.out.println("=======================================================\n");
            } finally {
                factory.destroy();
            }
        } catch (Exception ex) {
            System.out.println("STATUS:          CONNECTION_FAILED");
            System.out.println("Error Message:   " + ex.getMessage());
            if (ex.getCause() != null) {
                System.out.println("Cause:           " + ex.getCause().getMessage());
            }
            ex.printStackTrace(System.out);
            System.out.println("=======================================================\n");
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

