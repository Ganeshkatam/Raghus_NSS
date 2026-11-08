package edu.college.nss.security;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Strict database identity guard enforcing positive identification of isolated test databases.
 *
 * Rejects:
 * 1. All known cloud, hosted, or production providers (denylist defense-in-depth).
 * 2. Any non-loopback external host or IP (positive host allowlist).
 * 3. Any non-test database name, preventing accidental mutations to developer or production databases
 *    even on localhost (e.g., rejecting 'postgres', 'rec_nss', 'production').
 *
 * Accepts ONLY:
 * 1. Testcontainers ephemeral containers (jdbc:tc:... or dynamic loopback container URLs).
 * 2. In-memory test databases (jdbc:h2:mem:...).
 * 3. Dedicated local PostgreSQL instances on loopback (localhost, 127.0.0.1, [::1])
 *    targeting an explicitly approved disposable test database (e.g., 'rec_nss_test').
 */
public final class DatabaseIdentityGuard {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
        "supabase",
        "render.com",
        "onrender.com",
        "amazonaws.com",
        "rds.amazonaws",
        "production",
        "prod",
        "live",
        "main",
        "master",
        "stage",
        "staging"
    );

    private static final Set<String> ALLOWED_LOOPBACK_HOSTS = Set.of(
        "localhost",
        "127.0.0.1",
        "[::1]",
        "::1"
    );

    private static final Set<String> EXACT_APPROVED_TEST_DB_NAMES = Set.of(
        "rec_nss_test",
        "rec_nss_schema_test",
        "rec_nss_concurrency_test",
        "nss_test"
    );

    private DatabaseIdentityGuard() {
    }

    /**
     * Positively asserts that the given database JDBC URL targets an isolated,
     * disposable test database and NOT a remote, cloud, or non-test database.
     *
     * @param url JDBC database URL
     * @throws SecurityException if the URL fails positive test identity verification
     * @throws IllegalArgumentException if the URL is null or blank
     */
    public static void assertSafeTestDatabase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Database URL cannot be null or blank");
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);

        // 1. Defense-in-depth denylist check
        for (String forbidden : FORBIDDEN_SUBSTRINGS) {
            if (lowerUrl.contains(forbidden)) {
                throw new SecurityException(
                    "CRITICAL SECURITY VIOLATION: Database URL contains prohibited keyword '" + forbidden
                    + "'. Refused URL: " + url
                );
            }
        }

        // 2. In-memory H2 allowlist
        if (lowerUrl.startsWith("jdbc:h2:mem:")) {
            return;
        }

        // 3. Testcontainers tc: protocol allowlist
        if (lowerUrl.startsWith("jdbc:tc:")) {
            String dbName = extractDatabaseNameFromUrl(lowerUrl);
            if (dbName != null && !isApprovedTestDatabaseName(dbName)) {
                throw new SecurityException(
                    "CRITICAL SECURITY VIOLATION: Testcontainers URL specifies non-test database '" + dbName
                    + "'. Only dedicated test database names are permitted: " + url
                );
            }
            return;
        }

        // 4. PostgreSQL local loopback allowlist
        if (lowerUrl.startsWith("jdbc:postgresql://")) {
            String host = extractHostFromPostgresUrl(lowerUrl);
            if (!ALLOWED_LOOPBACK_HOSTS.contains(host)) {
                throw new SecurityException(
                    "CRITICAL SECURITY VIOLATION: Host '" + host + "' is not an approved loopback test host. "
                    + "Only loopback hosts (" + ALLOWED_LOOPBACK_HOSTS + ") or Testcontainers are permitted: " + url
                );
            }

            String dbName = extractDatabaseNameFromUrl(lowerUrl);
            if (!isApprovedTestDatabaseName(dbName)) {
                throw new SecurityException(
                    "CRITICAL SECURITY VIOLATION: Database name '" + dbName + "' is not a recognized disposable test database. "
                    + "Test databases must be dedicated test instances (e.g. 'rec_nss_test' or ending in '_test'): " + url
                );
            }

            return;
        }

        // Any other unrecognized protocol is refused
        throw new SecurityException(
            "CRITICAL SECURITY VIOLATION: Unrecognized or unsupported database protocol in test URL: " + url
        );
    }

    /**
     * Returns true if the database URL positively satisfies all test database identity requirements.
     */
    public static boolean isSafeTestDatabase(String url) {
        try {
            assertSafeTestDatabase(url);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractHostFromPostgresUrl(String lowerUrl) {
        String withoutPrefix = lowerUrl.substring("jdbc:postgresql://".length());
        int queryIdx = withoutPrefix.indexOf('?');
        String hostAndPath = queryIdx >= 0 ? withoutPrefix.substring(0, queryIdx) : withoutPrefix;
        int slashIdx = hostAndPath.indexOf('/');
        String hostPort = slashIdx >= 0 ? hostAndPath.substring(0, slashIdx) : hostAndPath;

        if (hostPort.startsWith("[")) {
            int closeBracket = hostPort.indexOf(']');
            if (closeBracket >= 0) {
                return hostPort.substring(0, closeBracket + 1);
            }
        }
        int colonIdx = hostPort.indexOf(':');
        return colonIdx >= 0 ? hostPort.substring(0, colonIdx) : hostPort;
    }

    private static String extractDatabaseNameFromUrl(String lowerUrl) {
        int queryIdx = lowerUrl.indexOf('?');
        String urlWithoutQuery = queryIdx >= 0 ? lowerUrl.substring(0, queryIdx) : lowerUrl;
        int lastSlashIdx = urlWithoutQuery.lastIndexOf('/');
        if (lastSlashIdx < 0 || lastSlashIdx == urlWithoutQuery.length() - 1) {
            return null;
        }
        return urlWithoutQuery.substring(lastSlashIdx + 1);
    }

    private static boolean isApprovedTestDatabaseName(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return false;
        }
        for (String forbidden : FORBIDDEN_SUBSTRINGS) {
            if (dbName.contains(forbidden)) {
                return false;
            }
        }
        if (EXACT_APPROVED_TEST_DB_NAMES.contains(dbName)) {
            return true;
        }
        // Strict pattern: must start with rec_nss_ or nss_ or test_ AND end with _test or _testing
        return (dbName.startsWith("rec_nss_") || dbName.startsWith("nss_") || dbName.startsWith("test_"))
            && (dbName.endsWith("_test") || dbName.endsWith("_testing"));
    }
}
