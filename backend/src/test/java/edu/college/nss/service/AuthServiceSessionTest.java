package edu.college.nss.service;

import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.security.CustomUserDetails;
import edu.college.nss.security.JwtTokenProvider;
import edu.college.nss.security.RedisTokenBlacklistService;
import edu.college.nss.security.TokenHashUtil;
import edu.college.nss.web.dto.AuthResponse;
import edu.college.nss.web.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceSessionTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisTokenBlacklistService tokenBlacklistService;

    @Mock
    private AuthSessionRepository authSessionRepository;

    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 900000L, 604800000L);
        authService = new AuthService(
            authenticationManager,
            tokenProvider,
            userRepository,
            passwordEncoder,
            tokenBlacklistService,
            authSessionRepository
        );
    }

    @Test
    void login_shouldPersistAuthSessionWithHashedRefreshTokenAndMetadata() {
        UUID userId = UUID.randomUUID();
        User user = new User("Session User", "session@college.edu", "hashedPw", "9876543210");
        user.setUserId(userId);
        user.setStatus("ACTIVE");

        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("session@college.edu")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("session@college.edu", "Password123");
        AuthResponse response = authService.login(request, "192.168.1.50", "Mozilla/5.0 Chrome");

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(sessionCaptor.capture());

        AuthSession savedSession = sessionCaptor.getValue();
        assertNotNull(savedSession.getSessionId());
        assertNotNull(savedSession.getTokenFamilyId());
        assertEquals(user, savedSession.getUser());
        assertEquals("192.168.1.50", savedSession.getIpAddress());
        assertEquals("Mozilla/5.0 Chrome", savedSession.getUserAgent());
        assertTrue(savedSession.isActive());
        assertNull(savedSession.getRevokedAt());

        // Verify hash matches the returned plaintext refresh token
        assertEquals(TokenHashUtil.sha256(response.refreshToken()), savedSession.getRefreshTokenHash());

        // Verify the generated access token carries the sessionId claim
        UUID sidFromToken = tokenProvider.getSessionIdFromToken(response.accessToken());
        assertEquals(savedSession.getSessionId(), sidFromToken);
    }

    @Test
    void logout_withSessionBearingToken_shouldRevokeAuthSession() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String accessToken = tokenProvider.generateAccessTokenFromEmail("test@college.edu", userId, "Test User", sessionId);

        User user = new User("Test User", "test@college.edu", "hash", "123");
        AuthSession session = new AuthSession(
            sessionId,
            user,
            UUID.randomUUID(),
            "tokenHash123",
            "127.0.0.1",
            "Agent",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );

        when(authSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        authService.logout("Bearer " + accessToken);

        assertFalse(session.isActive());
        assertEquals("USER_LOGOUT", session.getRevokeReason());
        assertNotNull(session.getRevokedAt());
        verify(authSessionRepository).save(session);
    }

    @Test
    void refresh_withValidActiveSession_shouldRotateRefreshTokenAndReturnNewTokens() {
        String oldRefreshToken = tokenProvider.generateRefreshToken("session@college.edu");
        String oldHash = TokenHashUtil.sha256(oldRefreshToken);

        UUID familyId = UUID.randomUUID();
        User user = new User("Session User", "session@college.edu", "hashedPw", "9876543210");
        user.setUserId(UUID.randomUUID());
        user.setStatus("ACTIVE");

        AuthSession activeSession = new AuthSession(
            UUID.randomUUID(),
            user,
            familyId,
            oldHash,
            "127.0.0.1",
            "Agent",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );

        when(userRepository.findByEmail("session@college.edu")).thenReturn(Optional.of(user));
        when(authSessionRepository.findByRefreshTokenHashWithLock(oldHash)).thenReturn(Optional.of(activeSession));

        AuthResponse response = authService.refresh(new edu.college.nss.web.dto.RefreshRequest(oldRefreshToken));

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotEquals(oldRefreshToken, response.refreshToken());

        // Old session must be marked TOKEN_ROTATED
        assertFalse(activeSession.isActive());
        assertEquals("TOKEN_ROTATED", activeSession.getRevokeReason());

        // New session must be saved with same familyId and new token hash
        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository, atLeast(2)).save(captor.capture());

        List<AuthSession> allSaved = captor.getAllValues();
        AuthSession newSession = allSaved.get(allSaved.size() - 1);

        assertTrue(newSession.isActive());
        assertEquals(familyId, newSession.getTokenFamilyId());
        assertEquals(TokenHashUtil.sha256(response.refreshToken()), newSession.getRefreshTokenHash());
        assertEquals(newSession.getSessionId(), tokenProvider.getSessionIdFromToken(response.accessToken()));
    }

    @Test
    void refresh_withAlreadyRotatedToken_shouldTriggerReuseDetectionAndRevokeFamily() {
        String rotatedRefreshToken = tokenProvider.generateRefreshToken("session@college.edu");
        String rotatedHash = TokenHashUtil.sha256(rotatedRefreshToken);

        UUID familyId = UUID.randomUUID();
        User user = new User("Session User", "session@college.edu", "hashedPw", "9876543210");
        user.setUserId(UUID.randomUUID());

        AuthSession rotatedSession = new AuthSession(
            UUID.randomUUID(),
            user,
            familyId,
            rotatedHash,
            "127.0.0.1",
            "Agent",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );
        rotatedSession.revoke("TOKEN_ROTATED");

        AuthSession currentActiveSession = new AuthSession(
            UUID.randomUUID(),
            user,
            familyId,
            "currentActiveHash",
            "127.0.0.1",
            "Agent",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );

        when(userRepository.findByEmail("session@college.edu")).thenReturn(Optional.of(user));
        when(authSessionRepository.findByRefreshTokenHashWithLock(rotatedHash)).thenReturn(Optional.of(rotatedSession));
        when(authSessionRepository.findByTokenFamilyIdAndIsActiveTrue(familyId)).thenReturn(List.of(currentActiveSession));

        org.springframework.security.authentication.BadCredentialsException ex = assertThrows(
            org.springframework.security.authentication.BadCredentialsException.class,
            () -> authService.refresh(new edu.college.nss.web.dto.RefreshRequest(rotatedRefreshToken))
        );

        assertTrue(ex.getMessage().contains("reuse detected"));

        // Current active session in the family must be revoked
        assertFalse(currentActiveSession.isActive());
        assertEquals("FAMILY_REVOKED", currentActiveSession.getRevokeReason());
        verify(authSessionRepository).save(currentActiveSession);
    }

    @Test
    void refresh_withRevokedSession_shouldThrowBadCredentialsException() {
        String token = tokenProvider.generateRefreshToken("session@college.edu");
        String hash = TokenHashUtil.sha256(token);

        User user = new User("Session User", "session@college.edu", "hashedPw", "9876543210");
        AuthSession revokedSession = new AuthSession(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            hash,
            "127.0.0.1",
            "Agent",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );
        revokedSession.revoke("USER_LOGOUT");

        when(userRepository.findByEmail("session@college.edu")).thenReturn(Optional.of(user));
        when(authSessionRepository.findByRefreshTokenHashWithLock(hash)).thenReturn(Optional.of(revokedSession));

        assertThrows(
            org.springframework.security.authentication.BadCredentialsException.class,
            () -> authService.refresh(new edu.college.nss.web.dto.RefreshRequest(token))
        );
    }

    @Test
    void refresh_withExpiredSession_shouldThrowBadCredentialsExceptionAndMarkExpired() {
        String token = tokenProvider.generateRefreshToken("session@college.edu");
        String hash = TokenHashUtil.sha256(token);

        User user = new User("Session User", "session@college.edu", "hashedPw", "9876543210");
        AuthSession expiredSession = new AuthSession(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            hash,
            "127.0.0.1",
            "Agent",
            "Chrome",
            Instant.now().minusSeconds(100) // expired
        );

        when(userRepository.findByEmail("session@college.edu")).thenReturn(Optional.of(user));
        when(authSessionRepository.findByRefreshTokenHashWithLock(hash)).thenReturn(Optional.of(expiredSession));

        assertThrows(
            org.springframework.security.authentication.BadCredentialsException.class,
            () -> authService.refresh(new edu.college.nss.web.dto.RefreshRequest(token))
        );

        assertFalse(expiredSession.isActive());
        assertEquals("EXPIRED", expiredSession.getRevokeReason());
        verify(authSessionRepository).save(expiredSession);
    }

    @Test
    void changePassword_shouldRevokeAllActiveSessionsForUser() {
        UUID userId = UUID.randomUUID();
        User user = new User("Session User", "session@college.edu", "$2a$10$hashedPw", "9876543210");
        user.setUserId(userId);

        when(userRepository.findByEmail("session@college.edu")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPass123", "$2a$10$hashedPw")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("$2a$10$newHashedPw");

        AuthSession activeSession1 = new AuthSession(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            "hash1",
            "127.0.0.1",
            "Agent1",
            "Chrome",
            Instant.now().plusSeconds(3600)
        );

        AuthSession activeSession2 = new AuthSession(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            "hash2",
            "127.0.0.2",
            "Agent2",
            "Safari",
            Instant.now().plusSeconds(3600)
        );

        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(userId)).thenReturn(List.of(activeSession1, activeSession2));

        authService.changePassword("session@college.edu", new edu.college.nss.web.dto.ChangePasswordRequest("CurrentPass123", "NewPassword456"));

        assertFalse(activeSession1.isActive());
        assertEquals("PASSWORD_CHANGED", activeSession1.getRevokeReason());
        assertFalse(activeSession2.isActive());
        assertEquals("PASSWORD_CHANGED", activeSession2.getRevokeReason());

        verify(authSessionRepository).save(activeSession1);
        verify(authSessionRepository).save(activeSession2);
    }
}
