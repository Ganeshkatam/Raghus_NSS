package edu.college.nss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NssApplication {
    public static void main(String[] args) {
        normalizeCloudDatabaseUrl();
        normalizeCloudRedisUrl();
        SpringApplication.run(NssApplication.class, args);
    }

    private static void normalizeCloudRedisUrl() {
        String redisUrl = System.getenv("SPRING_DATA_REDIS_URL");
        if (redisUrl == null || redisUrl.isBlank()) {
            redisUrl = System.getenv("REDIS_URL");
        }
        if (redisUrl != null && !redisUrl.isBlank()) {
            System.setProperty("spring.data.redis.url", redisUrl);
        }
    }

    private static void normalizeCloudDatabaseUrl() {
        String dbUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getenv("DATABASE_URL");
        }
        if (dbUrl != null && (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://"))) {
            try {
                java.net.URI uri = java.net.URI.create(dbUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                System.setProperty("spring.datasource.url", "jdbc:postgresql://" + host + ":" + port + path);
                if (uri.getUserInfo() != null) {
                    String[] credentials = uri.getUserInfo().split(":", 2);
                    System.setProperty("spring.datasource.username", credentials[0]);
                    if (credentials.length > 1) {
                        System.setProperty("spring.datasource.password", credentials[1]);
                    }
                }
            } catch (Exception ignored) {
                // Fall back to standard Spring datasource resolution
            }
        }
    }
}
