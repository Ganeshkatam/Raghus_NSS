package edu.college.nss.deployment;

import org.flywaydb.core.Flyway;

import java.io.File;
import java.nio.file.Files;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controlled manual runner for remote/production database read-only diagnostics and migration.
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

        System.out.println("================================================================================");
        System.out.println("REMOTE DATABASE RUNNER: " + command.toUpperCase());
        System.out.println("Target: " + maskDatabaseUrl(url));
        System.out.println("User:   " + maskUsername(user));
        System.out.println("================================================================================");

        if ("migrate".equals(command)) {
            executeControlledMigration(url, user, pass);
        } else {
            executeReadOnlyDiagnostics(url, user, pass);
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

    private static void executeReadOnlyDiagnostics(String url, String user, String pass) {
        System.out.println("Executing read-only diagnostics against production database...\n");
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            // Diagnostic 0: Basic Connection Context
            try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, session_user, version()")) {
                if (rs.next()) {
                    System.out.println("[DIAGNOSTIC 0] Connection Context:");
                    System.out.println("  Database:     " + rs.getString(1));
                    System.out.println("  Current User: " + rs.getString(2));
                    System.out.println("  Session User: " + rs.getString(3));
                    System.out.println("  Engine:       " + rs.getString(4));
                }
            }

            // Diagnostic 1: Roles, Superuser, and RLS Bypass Attributes
            System.out.println("\n[DIAGNOSTIC 1] Database Roles & RLS Bypass Attributes (pg_roles):");
            String rolesQuery = """
                SELECT 
                    rolname,
                    rolsuper,
                    rolinherit,
                    rolcreaterole,
                    rolcreatedb,
                    rolcanlogin,
                    rolbypassrls
                FROM pg_roles
                WHERE rolname IN (current_user, 'anon', 'authenticated', 'service_role', 'postgres', 'authenticator')
                ORDER BY rolname;
                """;
            try (ResultSet rs = stmt.executeQuery(rolesQuery)) {
                System.out.printf("  %-25s | %-6s | %-8s | %-8s | %-6s | %-12s%n",
                    "Role Name", "Super", "Inherit", "CreateDb", "Login", "BypassRLS");
                System.out.println("  -----------------------------------------------------------------------------");
                while (rs.next()) {
                    System.out.printf("  %-25s | %-6s | %-8s | %-8s | %-6s | %-12s%n",
                        rs.getString("rolname"),
                        rs.getBoolean("rolsuper"),
                        rs.getBoolean("rolinherit"),
                        rs.getBoolean("rolcreatedb"),
                        rs.getBoolean("rolcanlogin"),
                        rs.getBoolean("rolbypassrls"));
                }
            }

            // Diagnostic 2: Role Memberships & Inheritance (pg_auth_members)
            System.out.println("\n[DIAGNOSTIC 2] Role Memberships & Inheritance (pg_auth_members):");
            String membersQuery = """
                SELECT 
                    m.roleid::regrole::text AS member_of_role,
                    m.member::regrole::text AS role_member,
                    m.admin_option
                FROM pg_auth_members m
                WHERE m.member::regrole::text IN (current_user, 'anon', 'authenticated', 'service_role', 'postgres', 'authenticator')
                   OR m.roleid::regrole::text IN (current_user, 'anon', 'authenticated', 'service_role', 'postgres', 'authenticator')
                ORDER BY member_of_role, role_member;
                """;
            try (ResultSet rs = stmt.executeQuery(membersQuery)) {
                System.out.printf("  %-25s -> %-25s | Admin: %s%n", "Member of Role", "Role Member", "Opt");
                System.out.println("  -----------------------------------------------------------------------------");
                boolean foundAny = false;
                while (rs.next()) {
                    foundAny = true;
                    System.out.printf("  %-25s -> %-25s | Admin: %s%n",
                        rs.getString("member_of_role"),
                        rs.getString("role_member"),
                        rs.getBoolean("admin_option"));
                }
                if (!foundAny) {
                    System.out.println("  (No direct memberships found for target roles)");
                }
            }

            // Diagnostic 3: Every Table's Owner, RLS State, FORCE RLS, and Relation ACL
            System.out.println("\n[DIAGNOSTIC 3] Table Ownership, RLS, Force RLS, & RelACL (pg_class):");
            String tablesQuery = """
                SELECT 
                    c.relname AS table_name,
                    pg_get_userbyid(c.relowner) AS table_owner,
                    c.relrowsecurity AS rls_enabled,
                    c.relforcerowsecurity AS force_rls,
                    c.relacl::text AS relation_acl
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                ORDER BY c.relname;
                """;
            try (ResultSet rs = stmt.executeQuery(tablesQuery)) {
                System.out.printf("  %-28s | %-16s | %-6s | %-10s | %s%n",
                    "Table Name", "Owner", "RLS", "ForceRLS", "Relation ACL");
                System.out.println("  -----------------------------------------------------------------------------------------------------");
                int tableCount = 0;
                while (rs.next()) {
                    tableCount++;
                    String acl = rs.getString("relation_acl");
                    System.out.printf("  %-28s | %-16s | %-6s | %-10s | %s%n",
                        rs.getString("table_name"),
                        rs.getString("table_owner"),
                        rs.getBoolean("rls_enabled"),
                        rs.getBoolean("force_rls"),
                        acl != null ? (acl.length() > 60 ? acl.substring(0, 57) + "..." : acl) : "DEFAULT (None explicit)");
                }
                System.out.println("  Total Public Tables: " + tableCount);
            }

            // Diagnostic 4: Grants to anon, authenticated, and PUBLIC in information_schema
            System.out.println("\n[DIAGNOSTIC 4] Role Grants to anon, authenticated, & PUBLIC:");
            String grantsQuery = """
                SELECT 
                    grantee,
                    COUNT(*) AS table_count,
                    ARRAY_AGG(DISTINCT privilege_type ORDER BY privilege_type) AS privileges
                FROM information_schema.role_table_grants
                WHERE table_schema = 'public'
                  AND grantee IN ('anon', 'authenticated', 'PUBLIC')
                GROUP BY grantee
                ORDER BY grantee;
                """;
            try (ResultSet rs = stmt.executeQuery(grantsQuery)) {
                System.out.printf("  %-16s | %-12s | %s%n", "Grantee", "Tables Count", "Privileges");
                System.out.println("  -----------------------------------------------------------------------------");
                boolean foundGrants = false;
                while (rs.next()) {
                    foundGrants = true;
                    Array privArray = rs.getArray("privileges");
                    Object[] privs = privArray != null ? (Object[]) privArray.getArray() : new Object[0];
                    System.out.printf("  %-16s | %-12d | %s%n",
                        rs.getString("grantee"),
                        rs.getInt("table_count"),
                        Arrays.toString(privs));
                }
                if (!foundGrants) {
                    System.out.println("  (No direct grants recorded in information_schema for anon/authenticated/PUBLIC)");
                }
            }

            // Diagnostic 5: Locked Administrator Account Verification
            System.out.println("\n[DIAGNOSTIC 5] Administrator Account State (users & roles):");
            String adminQuery = """
                SELECT 
                    u.user_id,
                    u.name,
                    u.email,
                    u.status,
                    u.must_change_password,
                    ARRAY_AGG(r.name) AS roles
                FROM users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN roles r ON r.role_id = ur.role_id
                WHERE u.email IN ('katamganesh61@gmail.com', 'admin@raghunss.edu')
                   OR r.name IN ('ADMIN', 'ROLE_ADMIN')
                GROUP BY u.user_id, u.name, u.email, u.status, u.must_change_password
                ORDER BY u.email;
                """;
            try (ResultSet rs = stmt.executeQuery(adminQuery)) {
                System.out.printf("  %-36s | %-26s | %-10s | %-8s | %s%n",
                    "User ID", "Email", "Status", "MustChg", "Roles");
                System.out.println("  -----------------------------------------------------------------------------------------------------");
                boolean foundAdmin = false;
                while (rs.next()) {
                    foundAdmin = true;
                    Array rolesArray = rs.getArray("roles");
                    Object[] roles = rolesArray != null ? (Object[]) rolesArray.getArray() : new Object[0];
                    System.out.printf("  %-36s | %-26s | %-10s | %-8s | %s%n",
                        rs.getString("user_id"),
                        rs.getString("email"),
                        rs.getString("status"),
                        rs.getBoolean("must_change_password"),
                        Arrays.toString(roles));
                }
                if (!foundAdmin) {
                    System.out.println("  (No administrator accounts matching target emails or roles found)");
                }
            }

            // Diagnostic 6: Check Constraint on attendance_records.check_in_method
            System.out.println("\n[DIAGNOSTIC 6] Check Constraints on attendance_records:");
            String attCheckQuery = """
                SELECT 
                    c.conname,
                    pg_get_constraintdef(c.oid) AS def,
                    ARRAY(
                        SELECT a.attname 
                        FROM unnest(c.conkey) k 
                        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k
                    ) AS columns
                FROM pg_constraint c
                WHERE c.conrelid = 'attendance_records'::regclass 
                  AND c.contype = 'c'
                ORDER BY c.conname;
                """;
            try (ResultSet rs = stmt.executeQuery(attCheckQuery)) {
                while (rs.next()) {
                    Array colsArray = rs.getArray("columns");
                    Object[] cols = colsArray != null ? (Object[]) colsArray.getArray() : new Object[0];
                    System.out.println("  Constraint: " + rs.getString("conname"));
                    System.out.println("    Columns:  " + Arrays.toString(cols));
                    System.out.println("    Def:      " + rs.getString("def"));
                }
            }

            // Diagnostic 7: Check for Existing Duplicate Active Service Hour Entries
            System.out.println("\n[DIAGNOSTIC 7] Duplicate Active Service-Hour Claims Check:");
            String dupQuery = """
                SELECT 
                    volunteer_id,
                    event_id,
                    COUNT(*) AS total_entries,
                    COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved_count,
                    COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_count,
                    ARRAY_AGG(status) AS statuses
                FROM service_hour_entries
                WHERE event_id IS NOT NULL 
                  AND status IN ('PENDING', 'APPROVED')
                GROUP BY volunteer_id, event_id
                HAVING COUNT(*) > 1;
                """;
            try (ResultSet rs = stmt.executeQuery(dupQuery)) {
                boolean foundDups = false;
                while (rs.next()) {
                    foundDups = true;
                    Array stArray = rs.getArray("statuses");
                    Object[] sts = stArray != null ? (Object[]) stArray.getArray() : new Object[0];
                    System.out.printf("  DUPLICATE DETECTED: Volunteer=%s, Event=%s, Total=%d, Approved=%d, Pending=%d, Statuses=%s%n",
                        rs.getString("volunteer_id"),
                        rs.getString("event_id"),
                        rs.getInt("total_entries"),
                        rs.getInt("approved_count"),
                        rs.getInt("pending_count"),
                        Arrays.toString(sts));
                }
                if (!foundDups) {
                    System.out.println("  Zero existing duplicate active service-hour entries found across (volunteer_id, event_id).");
                }
            }

            System.out.println("\n================================================================================");
            System.out.println("READ-ONLY DIAGNOSTICS COMPLETED SUCCESSFULLY (No database writes performed)");
            System.out.println("================================================================================");

        } catch (Exception ex) {
            System.err.println("Diagnostic execution failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(3);
        }
    }

    public static String maskDatabaseUrl(String url) {
        if (url == null) return "null";
        int queryIdx = url.indexOf('?');
        String base = queryIdx >= 0 ? url.substring(0, queryIdx) : url;
        String maskedBase = base.replaceAll("://[^:@]+:[^@]+@", "://[REDACTED]@[REDACTED]");
        return maskedBase + " [QUERY PARAMS REDACTED]";
    }

    public static String maskUsername(String user) {
        if (user == null || user.isBlank()) return "null";
        if (user.length() <= 8) return "***";
        return user.substring(0, 4) + "***" + user.substring(user.length() - 4);
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
