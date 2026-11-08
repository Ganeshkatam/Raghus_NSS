package edu.college.nss.deployment;

import org.flywaydb.core.Flyway;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Controlled manual runner for remote/production database verification and migration.
 * This class is intentionally separated from JUnit to ensure automated tests never
 * mutate or migrate a shared remote database.
 */
public class LiveDatabaseDeploymentRunner {

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0].toLowerCase() : "verify";
        Map<String, String> env = loadEnvFile();

        String url = env.getOrDefault("SPRING_DATASOURCE_URL", System.getenv("SPRING_DATASOURCE_URL"));
        String user = env.getOrDefault("SPRING_DATASOURCE_USERNAME", System.getenv("SPRING_DATASOURCE_USERNAME"));
        String pass = env.getOrDefault("SPRING_DATASOURCE_PASSWORD", System.getenv("SPRING_DATASOURCE_PASSWORD"));

        if (url == null || url.isBlank() || pass == null || pass.isBlank() || "your_supabase_db_password".equals(pass)) {
            System.err.println("ABORTED: Valid SPRING_DATASOURCE_URL and SPRING_DATASOURCE_PASSWORD must be configured.");
            System.exit(1);
        }

        if (!url.contains("connectTimeout")) {
            url = url + (url.contains("?") ? "&" : "?") + "connectTimeout=30&socketTimeout=60";
        }

        System.out.println("=======================================================");
        System.out.println("REMOTE DATABASE DEPLOYMENT RUNNER: " + command.toUpperCase());
        System.out.println("Target: " + url);
        System.out.println("User:   " + user);
        System.out.println("=======================================================");

        if ("migrate".equals(command)) {
            executeControlledMigration(url, user, pass);
        } else {
            executeReadOnlyVerification(url, user, pass);
        }
    }

    private static void executeControlledMigration(String url, String user, String pass) {
        String confirmed = System.getenv("CONFIRM_REMOTE_MIGRATION");
        if (!"true".equalsIgnoreCase(confirmed)) {
            System.err.println("MIGRATION REJECTED: Remote migrations require explicit confirmation via CONFIRM_REMOTE_MIGRATION=true.");
            System.exit(2);
        }

        System.out.println("Applying Flyway migrations to remote database...");
        Flyway flyway = Flyway.configure()
            .dataSource(url, user, pass)
            .locations("classpath:db/migration")
            .load();

        var infoBefore = flyway.info().current();
        System.out.println("Current Schema Version before migration: " + (infoBefore != null ? infoBefore.getVersion() : "NONE"));

        var result = flyway.migrate();
        System.out.println("Migrations Executed: " + result.migrationsExecuted);
        System.out.println("Target Schema Version now: " + result.targetSchemaVersion);
        System.out.println("Flyway migration completed successfully.");
    }

    private static void executeReadOnlyVerification(String url, String user, String pass) {
        System.out.println("Executing read-only connectivity and constraint verification...");
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, version()")) {
                if (rs.next()) {
                    System.out.println("Database:       " + rs.getString(1));
                    System.out.println("Connected As:   " + rs.getString(2));
                    System.out.println("Engine Version: " + rs.getString(3));
                }
            }

            System.out.println("\nChecking service_hour_entries check constraints via pg_constraint and pg_attribute:");
            String catalogQuery = """
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
            try (ResultSet rs = stmt.executeQuery(catalogQuery)) {
                boolean foundStatusCheck = false;
                while (rs.next()) {
                    String name = rs.getString("conname");
                    String def = rs.getString("def");
                    java.sql.Array colsArray = rs.getArray("columns");
                    String[] cols = (String[]) colsArray.getArray();
                    List<String> colList = Arrays.asList(cols);
                    System.out.println("  Constraint: " + name);
                    System.out.println("    Columns:  " + colList);
                    System.out.println("    Def:      " + def);

                    if ("chk_service_hour_entries_status".equals(name)) {
                        foundStatusCheck = true;
                        if (colList.size() != 1 || !"status".equals(colList.get(0))) {
                            throw new IllegalStateException("INVARIANT VIOLATION: chk_service_hour_entries_status must constrain ONLY the 'status' column! Found: " + colList);
                        }

                        // Exact AST token extraction from ARRAY[...]
                        Set<String> parsedStatuses = new TreeSet<>();
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("'([^']+)'").matcher(def);
                        while (m.find()) {
                            String token = m.group(1);
                            if (!token.contains(" ") && !token.equalsIgnoreCase("text")) {
                                parsedStatuses.add(token.toUpperCase(java.util.Locale.ROOT));
                            }
                        }
                        Set<String> expectedStatuses = Set.of("APPROVED", "PENDING", "REJECTED", "REVOKED");
                        if (!expectedStatuses.equals(parsedStatuses)) {
                            throw new IllegalStateException("INVARIANT VIOLATION: Allowed statuses must be EXACTLY " + expectedStatuses + ", but found: " + parsedStatuses);
                        }
                        System.out.println("    -> VERIFIED EXACT SET: 'chk_service_hour_entries_status' constrains strictly ['status'] to exactly " + expectedStatuses + " (count=" + parsedStatuses.size() + ").");
                    }
                }
                if (!foundStatusCheck) {
                    throw new IllegalStateException("INVARIANT VIOLATION: chk_service_hour_entries_status was not found in pg_constraint!");
                }
            }

            System.out.println("\nVerification completed successfully.");
        } catch (Exception ex) {
            System.err.println("Verification failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(3);
        }
    }

    private static Map<String, String> loadEnvFile() {
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
