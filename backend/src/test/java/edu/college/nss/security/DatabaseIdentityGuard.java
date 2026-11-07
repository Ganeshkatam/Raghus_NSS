package edu.college.nss.security;

import java.util.List;
import java.util.Locale;

/**
 * Strict database identity guard to prevent test suites or local scripts from
 * executing destructive operations or concurrency tests against live, cloud, or production databases.
 */
public final class DatabaseIdentityGuard {

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
        "supabase.co",
        "supabase.com",
        "pooler.supabase",
        "render.com",
        "onrender.com",
        "amazonaws.com",
        "rds.amazonaws",
        "production",
        "prod"
    );

    private DatabaseIdentityGuard() {
    }

    /**
     * Asserts that the given database JDBC URL is an isolated local test database
     * and NOT a known cloud provider or production environment.
     *
     * @param url JDBC database URL
     * @throws SecurityException if the URL matches any cloud or production pattern
     * @throws IllegalArgumentException if the URL is null or blank
     */
    public static void assertSafeTestDatabase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Database URL cannot be null or blank");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern)) {
                throw new SecurityException(
                    "CRITICAL SECURITY VIOLATION: Execution against production or remote database is strictly prohibited! "
                    + "Refused connection to protected pattern '" + pattern + "' in URL: " + url
                );
            }
        }
    }

    /**
     * Returns true if the database URL targets an isolated local test environment.
     */
    public static boolean isSafeTestDatabase(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern)) {
                return false;
            }
        }
        return true;
    }
}
