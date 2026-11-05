package edu.college.nss.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.service.AuthService;
import edu.college.nss.web.dto.ChangePasswordRequest;
import edu.college.nss.web.dto.LoginRequest;
import edu.college.nss.web.dto.RefreshRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SessionAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private AuthSessionRepository authSessionRepository;

    @MockitoSpyBean
    private RedisTokenBlacklistService tokenBlacklistService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private User testUser;
    private static final String TEST_EMAIL = "session-sec-test@raghuenggcollege.in";
    private static final String TEST_PASSWORD = "ValidPassword123!";

    @BeforeEach
    void setUp() {
        authSessionRepository.deleteAll();

        testUser = userRepository.findByEmail(TEST_EMAIL).orElseGet(() -> {
            User u = new User("Session Test User", TEST_EMAIL, passwordEncoder.encode(TEST_PASSWORD), "9876543210");
            u.setStatus("ACTIVE");
            return userRepository.save(u);
        });
    }

    @AfterEach
    void tearDown() {
        authSessionRepository.deleteAll();
        if (testUser != null && testUser.getUserId() != null) {
            userRepository.deleteById(testUser.getUserId());
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private AuthSession createSession(UUID sessionId, User user, Instant expiresAt) {
        UUID tokenFamilyId = UUID.randomUUID();
        String refreshTokenHash = TokenHashUtil.sha256(UUID.randomUUID().toString());
        AuthSession session = new AuthSession(
            sessionId,
            user,
            tokenFamilyId,
            refreshTokenHash,
            "127.0.0.1",
            "IntegrationTestBrowser",
            "Test Device",
            expiresAt
        );
        return authSessionRepository.save(session);
    }

    @Test
    @DisplayName("P0.1: Refresh JWT passed to protected endpoint must return 401")
    void refreshToken_againstProtectedEndpoint_returns401() throws Exception {
        String refreshToken = tokenProvider.generateRefreshToken(testUser.getEmail());

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.1: Access JWT with missing type claim must return 401")
    void accessToken_withMissingType_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        // Craft valid JWT without 'type' claim
        String legacyToken = Jwts.builder()
            .subject(testUser.getEmail())
            .claim("userId", testUser.getUserId().toString())
            .claim("name", testUser.getName())
            .claim("sessionId", sessionId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(getSigningKey())
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + legacyToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Access JWT with missing sessionId claim must return 401")
    void accessToken_withMissingSessionId_returns401() throws Exception {
        String tokenNoSession = Jwts.builder()
            .subject(testUser.getEmail())
            .claim("type", "ACCESS")
            .claim("userId", testUser.getUserId().toString())
            .claim("name", testUser.getName())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(getSigningKey())
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + tokenNoSession))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Access JWT with missing userId claim must return 401")
    void accessToken_withMissingUserId_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String tokenNoUserId = Jwts.builder()
            .subject(testUser.getEmail())
            .claim("type", "ACCESS")
            .claim("name", testUser.getName())
            .claim("sessionId", sessionId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(getSigningKey())
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + tokenNoUserId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Access JWT where token userId differs from session userId returns 401")
    void accessToken_withMismatchedUserId_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        UUID randomDifferentUserId = UUID.randomUUID();
        String mismatchedToken = Jwts.builder()
            .subject(testUser.getEmail())
            .claim("type", "ACCESS")
            .claim("userId", randomDifferentUserId.toString())
            .claim("name", testUser.getName())
            .claim("sessionId", sessionId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(getSigningKey())
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + mismatchedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Access JWT where token email differs from session email returns 401")
    void accessToken_withMismatchedEmail_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String mismatchedEmailToken = Jwts.builder()
            .subject("different-user@raghuenggcollege.in")
            .claim("type", "ACCESS")
            .claim("userId", testUser.getUserId().toString())
            .claim("name", testUser.getName())
            .claim("sessionId", sessionId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(getSigningKey())
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + mismatchedEmailToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Valid access JWT with active persistent session returns 200")
    void accessToken_withActiveSession_returns200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String validAccessToken = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + validAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(testUser.getEmail()));
    }

    @Test
    @DisplayName("P0.2: Logged-out session invalidates access token immediately without Redis")
    void accessToken_withLoggedOutSession_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        // Perform logout (marks session revoked in PostgreSQL)
        authService.logout("Bearer " + token);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Password change revokes all active sessions immediately")
    void accessToken_withPasswordChangedSession_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        // Perform password change
        ChangePasswordRequest request = new ChangePasswordRequest(TEST_PASSWORD, "BrandNewPassword123!");
        authService.changePassword(testUser.getEmail(), request);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Expired session in database returns 401")
    void accessToken_withExpiredSession_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        // Session expired 5 minutes ago
        createSession(sessionId, testUser, Instant.now().minusSeconds(300));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Session expiring exactly at current instant returns 401")
    void accessToken_withSessionExpiringAtCurrentInstant_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        // Boundary condition: session expires at exact current instant
        createSession(sessionId, testUser, Instant.now());

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Unknown sessionId claim returns 401")
    void accessToken_withUnknownSessionId_returns401() throws Exception {
        UUID unknownSessionId = UUID.randomUUID();

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            unknownSessionId
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.2: Database outage fails closed with 503 Service Unavailable")
    void databaseFailure_duringSessionLookup_failsClosedWith503() throws Exception {
        UUID sessionId = UUID.randomUUID();

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        // Simulate PostgreSQL outage
        doThrow(new DataAccessResourceFailureException("PostgreSQL connection refused"))
            .when(authSessionRepository).findByIdWithUser(sessionId);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("SESSION_STORE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("P0.2: Redis outage does not block valid PostgreSQL session from authenticating")
    void redisUnavailable_postgreSqlAvailable_validSessionStillWorks() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        // Simulate Redis error
        doThrow(new RuntimeException("Redis connection timeout"))
            .when(tokenBlacklistService).isTokenBlacklisted(any());

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(testUser.getEmail()));
    }

    @Test
    @DisplayName("P0.2: Redis says blacklisted but PostgreSQL session valid - PostgreSQL remains authoritative and succeeds")
    void redisSaysBlacklisted_postgreSqlSessionValid_authenticationSucceeds() throws Exception {
        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        // Even if Redis claims the token is blacklisted, PostgreSQL session is authoritative
        when(tokenBlacklistService.isTokenBlacklisted(any())).thenReturn(true);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(testUser.getEmail()));
    }

    @Test
    @DisplayName("P0.2: Access JWT from rotated session is rejected")
    void accessToken_fromRotatedSession_returns401() throws Exception {
        UUID sessionId = UUID.randomUUID();
        AuthSession session = createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        // Mark session as rotated
        session.revoke("TOKEN_ROTATED");
        authSessionRepository.save(session);

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("P0.3: Locked administrator login is rejected")
    void lockedUser_login_isRejected() throws Exception {
        testUser.setStatus("LOCKED");
        userRepository.save(testUser);

        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P0.3: Locked administrator refresh is rejected")
    void lockedUser_refresh_isRejected() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String refreshToken = tokenProvider.generateRefreshToken(testUser.getEmail());
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);

        AuthSession session = new AuthSession(
            sessionId,
            testUser,
            UUID.randomUUID(),
            refreshTokenHash,
            "127.0.0.1",
            "IntegrationTestBrowser",
            "Test Device",
            Instant.now().plusSeconds(604800)
        );
        authSessionRepository.save(session);

        testUser.setStatus("LOCKED");
        userRepository.save(testUser);

        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P0.3: mustChangePassword=true blocks access to general protected endpoints with 403")
    void mustChangePassword_blocksGeneralProtectedEndpoint() throws Exception {
        testUser.setMustChangePassword(true);
        userRepository.save(testUser);

        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId,
            true
        );

        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @DisplayName("P0.3: mustChangePassword=true permits /change-password and successfully clears flag")
    void mustChangePassword_allowsChangePassword_andClearsFlag() throws Exception {
        testUser.setMustChangePassword(true);
        userRepository.save(testUser);

        UUID sessionId = UUID.randomUUID();
        createSession(sessionId, testUser, Instant.now().plusSeconds(900));

        String token = tokenProvider.generateAccessTokenFromEmail(
            testUser.getEmail(),
            testUser.getUserId(),
            testUser.getName(),
            sessionId,
            true
        );

        ChangePasswordRequest request = new ChangePasswordRequest(TEST_PASSWORD, "NewStrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        User updatedUser = userRepository.findByEmail(testUser.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(updatedUser.isMustChangePassword());
    }

    @Test
    @DisplayName("P0.3: Refresh with mustChangePassword=true preserves the requirement in new access token")
    void mustChangePassword_preservedAcrossRefresh() throws Exception {
        testUser.setMustChangePassword(true);
        userRepository.save(testUser);

        UUID sessionId = UUID.randomUUID();
        String refreshToken = tokenProvider.generateRefreshToken(testUser.getEmail());
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);

        AuthSession session = new AuthSession(
            sessionId,
            testUser,
            UUID.randomUUID(),
            refreshTokenHash,
            "127.0.0.1",
            "IntegrationTestBrowser",
            "Test Device",
            Instant.now().plusSeconds(604800)
        );
        authSessionRepository.save(session);

        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        edu.college.nss.web.dto.AuthResponse authResponse = objectMapper.readValue(
            responseJson, edu.college.nss.web.dto.AuthResponse.class
        );

        org.junit.jupiter.api.Assertions.assertTrue(tokenProvider.isMustChangePassword(authResponse.accessToken()));
        org.junit.jupiter.api.Assertions.assertTrue(authResponse.user().mustChangePassword());
    }
}
