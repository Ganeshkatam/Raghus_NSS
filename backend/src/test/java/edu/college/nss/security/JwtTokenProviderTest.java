package edu.college.nss.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenProviderTest {

    @Test
    void constructor_withNullSecret_shouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new JwtTokenProvider(null, 900000L, 604800000L)
        );
        assertTrue(ex.getMessage().contains("must be configured and at least 32 characters"));
    }

    @Test
    void constructor_withShortSecret_shouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new JwtTokenProvider("short-secret-key", 900000L, 604800000L)
        );
        assertTrue(ex.getMessage().contains("must be configured and at least 32 characters"));
    }

    @Test
    void constructor_withBlankSecret_shouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new JwtTokenProvider("                                ", 900000L, 604800000L)
        );
        assertTrue(ex.getMessage().contains("must be configured and at least 32 characters"));
    }

    @Test
    void constructor_withNonPositiveExpiration_shouldThrowIllegalArgumentException() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new JwtTokenProvider(validSecret, 0L, 604800000L)
        );
        assertTrue(ex.getMessage().contains("access token expiration must be greater than zero"));
    }

    @Test
    void constructor_withNonPositiveRefreshExpiration_shouldThrowIllegalArgumentException() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new JwtTokenProvider(validSecret, 900000L, -100L)
        );
        assertTrue(ex.getMessage().contains("refresh token expiration must be greater than zero"));
    }

    @Test
    void constructor_withValidSecret_shouldInitializeAndValidateToken() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        JwtTokenProvider provider = new JwtTokenProvider(validSecret, 900000L, 604800000L);

        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.UUID sessionId = java.util.UUID.randomUUID();
        String token = provider.generateAccessTokenFromEmail("test@college.edu", userId, "Test User", sessionId);

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertTrue(provider.isAccessToken(token));
        assertFalse(provider.isRefreshToken(token));
        assertEquals("test@college.edu", provider.getEmailFromToken(token));
        assertEquals(sessionId, provider.getSessionIdFromToken(token));
        assertEquals(userId, provider.getUserIdFromToken(token));
    }

    @Test
    void generateAccessToken_withNullUserId_throwsIllegalArgumentException() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        JwtTokenProvider provider = new JwtTokenProvider(validSecret, 900000L, 604800000L);
        java.util.UUID sessionId = java.util.UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
            provider.generateAccessTokenFromEmail("test@college.edu", null, "Test User", sessionId)
        );
    }

    @Test
    void generateAccessToken_withNullSessionId_throwsIllegalArgumentException() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        JwtTokenProvider provider = new JwtTokenProvider(validSecret, 900000L, 604800000L);
        java.util.UUID userId = java.util.UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
            provider.generateAccessTokenFromEmail("test@college.edu", userId, "Test User", null)
        );
    }

    @Test
    void generateAccessToken_withSessionId_shouldIncludeSessionIdClaim() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        JwtTokenProvider provider = new JwtTokenProvider(validSecret, 900000L, 604800000L);

        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.UUID sessionId = java.util.UUID.randomUUID();

        String token = provider.generateAccessTokenFromEmail("session-user@college.edu", userId, "Session User", sessionId);

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertTrue(provider.isAccessToken(token));
        assertFalse(provider.isRefreshToken(token));
        assertEquals(sessionId, provider.getSessionIdFromToken(token));
        assertEquals(604800000L, provider.getRefreshExpirationMs());
    }

    @Test
    void generateRefreshToken_shouldSetRefreshType() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        JwtTokenProvider provider = new JwtTokenProvider(validSecret, 900000L, 604800000L);

        String refreshToken = provider.generateRefreshToken("refresh-test@college.edu");

        assertNotNull(refreshToken);
        assertTrue(provider.validateToken(refreshToken));
        assertTrue(provider.isRefreshToken(refreshToken));
        assertFalse(provider.isAccessToken(refreshToken));
        assertEquals("refresh-test@college.edu", provider.getEmailFromToken(refreshToken));
    }
}
