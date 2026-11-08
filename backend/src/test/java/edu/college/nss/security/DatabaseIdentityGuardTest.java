package edu.college.nss.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Database Identity Guard Safety Test")
class DatabaseIdentityGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://db.ywbcufyjfwjxvgifcasx.supabase.co:5432/postgres",
        "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require",
        "jdbc:postgresql://dpg-cub1abc234-a.singapore-postgres.render.com/nss_db",
        "jdbc:postgresql://app.onrender.com/rec_nss",
        "jdbc:postgresql://rec-nss-db.c9abc123.ap-south-1.rds.amazonaws.com:5432/postgres",
        "jdbc:postgresql://my-aurora-cluster.cluster-xyz.amazonaws.com:5432/nss",
        "jdbc:postgresql://localhost:5432/rec_nss_production",
        "jdbc:postgresql://localhost:5432/prod_database",
        "jdbc:postgresql://10.0.0.1:5432/ProductionNSS",
        "jdbc:postgresql://10.0.0.1:5432/rec_nss_test",
        "jdbc:postgresql://192.168.1.100:5432/rec_nss_test",
        "jdbc:postgresql://remote-postgres.corp.internal:5432/rec_nss_test",
        "jdbc:postgresql://external-db.example.com:5432/rec_nss_test",
        "jdbc:postgresql://localhost:5432/postgres",
        "jdbc:postgresql://localhost:5432/rec_nss",
        "jdbc:postgresql://127.0.0.1:5432/rec_nss_dev",
        "jdbc:postgresql://127.0.0.1:5432/development",
        "jdbc:mysql://localhost:3306/rec_nss_test",
        "jdbc:oracle:thin:@localhost:1521:rec_nss_test"
    })
    @DisplayName("Should refuse known cloud, remote hosts, or non-test database names")
    void shouldRefuseCloudRemoteAndNonTestUrls(String prohibitedUrl) {
        SecurityException ex = assertThrows(
            SecurityException.class,
            () -> DatabaseIdentityGuard.assertSafeTestDatabase(prohibitedUrl),
            "Expected SecurityException for prohibited URL: " + prohibitedUrl
        );
        assertTrue(ex.getMessage().contains("CRITICAL SECURITY VIOLATION"));
        assertFalse(DatabaseIdentityGuard.isSafeTestDatabase(prohibitedUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://localhost:5432/rec_nss_test",
        "jdbc:postgresql://127.0.0.1:5432/rec_nss_test?sslmode=disable",
        "jdbc:postgresql://127.0.0.1:5433/rec_nss_concurrency_test",
        "jdbc:postgresql://[::1]:5432/rec_nss_schema_test",
        "jdbc:postgresql://localhost:5432/nss_test",
        "jdbc:tc:postgresql:17-alpine:///rec_nss_test",
        "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
    })
    @DisplayName("Should positively accept approved loopback test database URLs")
    void shouldAcceptApprovedTestUrls(String approvedTestUrl) {
        assertDoesNotThrow(() -> DatabaseIdentityGuard.assertSafeTestDatabase(approvedTestUrl));
        assertTrue(DatabaseIdentityGuard.isSafeTestDatabase(approvedTestUrl));
    }

    @Test
    @DisplayName("Should reject null or blank database URLs")
    void shouldRejectNullOrBlankUrls() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseIdentityGuard.assertSafeTestDatabase(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseIdentityGuard.assertSafeTestDatabase(""));
        assertThrows(IllegalArgumentException.class, () -> DatabaseIdentityGuard.assertSafeTestDatabase("   "));
        assertFalse(DatabaseIdentityGuard.isSafeTestDatabase(null));
        assertFalse(DatabaseIdentityGuard.isSafeTestDatabase(""));
    }
}
