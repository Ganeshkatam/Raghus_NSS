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
            System.out.println("=======================================================\n");
        }

        testRedisConnection(env.getOrDefault("SPRING_DATA_REDIS_URL", System.getenv("SPRING_DATA_REDIS_URL")));
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

