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
        "jdbc:postgresql://10.0.0.1:5432/ProductionNSS"
    })
    @DisplayName("Should refuse known cloud, remote, or production database URLs")
    void shouldRefuseCloudAndProductionUrls(String cloudOrProdUrl) {
        SecurityException ex = assertThrows(
            SecurityException.class,
            () -> DatabaseIdentityGuard.assertSafeTestDatabase(cloudOrProdUrl),
            "Expected SecurityException for prohibited URL: " + cloudOrProdUrl
        );
        assertTrue(ex.getMessage().contains("CRITICAL SECURITY VIOLATION"));
        assertFalse(DatabaseIdentityGuard.isSafeTestDatabase(cloudOrProdUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://localhost:5432/nss_test",
        "jdbc:postgresql://127.0.0.1:5432/test_rec_nss",
        "jdbc:postgresql://127.0.0.1:5433/isolated_test_db",
        "jdbc:tc:postgresql:17-alpine:///test_db",
        "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
    })
    @DisplayName("Should accept isolated local test database URLs")
    void shouldAcceptLocalTestUrls(String localTestUrl) {
        assertDoesNotThrow(() -> DatabaseIdentityGuard.assertSafeTestDatabase(localTestUrl));
        assertTrue(DatabaseIdentityGuard.isSafeTestDatabase(localTestUrl));
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
