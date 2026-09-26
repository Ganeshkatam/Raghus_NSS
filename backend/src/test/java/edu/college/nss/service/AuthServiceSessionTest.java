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
}
