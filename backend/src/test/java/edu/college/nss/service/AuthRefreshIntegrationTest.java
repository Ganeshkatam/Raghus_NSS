package edu.college.nss.service;

import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.security.JwtTokenProvider;
import edu.college.nss.web.dto.AuthResponse;
import edu.college.nss.web.dto.LoginRequest;
import edu.college.nss.web.dto.RefreshRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthRefreshIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private AuthSessionRepository authSessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider tokenProvider;

    private User testUser;
    private static final String TEST_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        authSessionRepository.deleteAll();

        testUser = userRepository.findByEmail("authtest@raghunss.edu").orElseGet(() -> {
            User u = new User("Auth Test User", "authtest@raghunss.edu", passwordEncoder.encode(TEST_PASSWORD), "9876543210");
            u.setStatus("ACTIVE");
            return userRepository.save(u);
        });
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        authSessionRepository.deleteAll();
        if (testUser != null && testUser.getUserId() != null) {
            userRepository.deleteById(testUser.getUserId());
        }
    }

    @Test
    void concurrentRefresh_withSameRefreshToken_onlyOneSucceedsAndOtherTriggersReplayDetection() throws Exception {
        AuthResponse loginResponse = authService.login(
            new LoginRequest(testUser.getEmail(), TEST_PASSWORD),
            "127.0.0.1",
            "IntegrationTestAgent"
        );
        String originalRefreshToken = loginResponse.refreshToken();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger reuseRevokeCount = new AtomicInteger(0);
        List<String> newRefreshTokens = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    AuthResponse res = authService.refresh(new RefreshRequest(originalRefreshToken));
                    successCount.incrementAndGet();
                    newRefreshTokens.add(res.refreshToken());
                } catch (BadCredentialsException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("reuse detected")) {
                        reuseRevokeCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            }));
        }

        readyLatch.countDown();
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly one request must succeed in rotating the token
        assertEquals(1, successCount.get(), "Exactly one concurrent refresh request must succeed");
        assertEquals(1, reuseRevokeCount.get(), "The second concurrent request must trigger reuse detection and fail");

        // The session family must now be revoked because the second request replayed the consumed token
        List<AuthSession> allSessions = authSessionRepository.findAll();
        assertFalse(allSessions.isEmpty());
        List<AuthSession> activeFamilySessions = authSessionRepository.findByTokenFamilyIdAndIsActiveTrue(
            allSessions.get(0).getTokenFamilyId()
        );
        assertTrue(activeFamilySessions.isEmpty(), "All sessions in the token family should be revoked following replay detection");
    }

    @Test
    void logout_thenRefreshWithOriginalRefreshToken_shouldBeRejected() {
        AuthResponse loginResponse = authService.login(
            new LoginRequest(testUser.getEmail(), TEST_PASSWORD),
            "127.0.0.1",
            "IntegrationTestAgent"
        );
        String accessToken = loginResponse.accessToken();
        String refreshToken = loginResponse.refreshToken();

        // Logout using the access token (extracts sessionId and revokes session)
        authService.logout("Bearer " + accessToken);

        // Attempting to refresh with the refresh token from that session must fail
        BadCredentialsException ex = assertThrows(
            BadCredentialsException.class,
            () -> authService.refresh(new RefreshRequest(refreshToken))
        );
        assertTrue(ex.getMessage().contains("USER_LOGOUT"));
    }

    @Test
    void refresh_thenReplayingOldToken_shouldRevokeFamilyAndRejectSubsequentNewTokenRefresh() {
        AuthResponse loginResponse = authService.login(
            new LoginRequest(testUser.getEmail(), TEST_PASSWORD),
            "127.0.0.1",
            "IntegrationTestAgent"
        );
        String r1 = loginResponse.refreshToken();

        // 1. First legitimate rotation: R1 -> R2
        AuthResponse refreshResponse1 = authService.refresh(new RefreshRequest(r1));
        String r2 = refreshResponse1.refreshToken();
        assertNotEquals(r1, r2);

        // 2. Attacker replays old rotated token R1
        BadCredentialsException replayEx = assertThrows(
            BadCredentialsException.class,
            () -> authService.refresh(new RefreshRequest(r1))
        );
        assertTrue(replayEx.getMessage().contains("reuse detected"));

        // 3. Legitimate client now tries to use R2; must be rejected because entire family was revoked
        BadCredentialsException siblingEx = assertThrows(
            BadCredentialsException.class,
            () -> authService.refresh(new RefreshRequest(r2))
        );
        assertTrue(siblingEx.getMessage().contains("FAMILY_REVOKED"));
    }

    @Test
    void refresh_withUnknownToken_shouldThrowBadCredentialsWithoutAffectingActiveFamilies() {
        AuthResponse loginResponse = authService.login(
            new LoginRequest(testUser.getEmail(), TEST_PASSWORD),
            "127.0.0.1",
            "IntegrationTestAgent"
        );
        String r1 = loginResponse.refreshToken();

        // Generate an arbitrary valid refresh JWT not known in database
        String unknownToken = tokenProvider.generateRefreshToken(testUser.getEmail());

        BadCredentialsException ex = assertThrows(
            BadCredentialsException.class,
            () -> authService.refresh(new RefreshRequest(unknownToken))
        );
        assertTrue(ex.getMessage().contains("Invalid or unrecognized refresh token"));

        // The user's active session R1 must still be active and usable
        AuthResponse validRefresh = authService.refresh(new RefreshRequest(r1));
        assertNotNull(validRefresh);
        assertNotNull(validRefresh.accessToken());
        assertNotNull(validRefresh.refreshToken());
    }
}
