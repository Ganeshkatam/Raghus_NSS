package edu.college.nss.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AuthSessionTest {

    @Test
    void newSession_shouldBeActiveAndValid() {
        User user = new User("Test User", "test@college.edu", "hash", "1234567890");
        UUID sessionId = UUID.randomUUID();
        UUID tokenFamilyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        AuthSession session = new AuthSession(
            sessionId,
            user,
            tokenFamilyId,
            "hash123",
            "127.0.0.1",
            "Mozilla/5.0",
            "Chrome",
            expiresAt
        );

        assertTrue(session.isActive());
        assertNull(session.getRevokedAt());
        assertNull(session.getRevokeReason());
        assertFalse(session.isExpired());
        assertTrue(session.isValid());
        assertEquals("hash123", session.getRefreshTokenHash());
        assertEquals(user, session.getUser());
    }

    @Test
    void revoke_shouldDeactivateAndRecordMetadata() {
        User user = new User("Test User", "test@college.edu", "hash", "1234567890");
        AuthSession session = new AuthSession(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            "hash123",
            "127.0.0.1",
            "Mozilla/5.0",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );

        session.revoke("USER_LOGOUT");

        assertFalse(session.isActive());
        assertNotNull(session.getRevokedAt());
        assertEquals("USER_LOGOUT", session.getRevokeReason());
        assertFalse(session.isValid());
    }

    @Test
    void isExpired_whenPastExpiresAt_shouldReturnTrue() {
        User user = new User("Test User", "test@college.edu", "hash", "1234567890");
        AuthSession session = new AuthSession(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            "hash123",
            "127.0.0.1",
            "Mozilla/5.0",
            "Chrome",
            Instant.now().minusSeconds(10)
        );

        assertTrue(session.isExpired());
        assertFalse(session.isValid());
    }
}
