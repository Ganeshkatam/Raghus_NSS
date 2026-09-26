package edu.college.nss.service;

import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.security.CustomUserDetails;
import edu.college.nss.security.JwtTokenProvider;
import edu.college.nss.security.TokenHashUtil;
import edu.college.nss.web.dto.AuthResponse;
import edu.college.nss.web.dto.LoginRequest;
import edu.college.nss.web.dto.RefreshRequest;
import edu.college.nss.web.dto.UserDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.college.nss.web.dto.ChangePasswordRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final edu.college.nss.security.RedisTokenBlacklistService tokenBlacklistService;
    private final AuthSessionRepository authSessionRepository;

    public AuthService(
        AuthenticationManager authenticationManager,
        JwtTokenProvider tokenProvider,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        edu.college.nss.security.RedisTokenBlacklistService tokenBlacklistService,
        AuthSessionRepository authSessionRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenBlacklistService = tokenBlacklistService;
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);

        // Revoke all previous active sessions in Redis upon password change
        tokenBlacklistService.blacklistAllUserTokens(email);

        // Revoke all active database refresh sessions
        List<AuthSession> activeSessions = authSessionRepository.findByUser_UserIdAndIsActiveTrue(user.getUserId());
        for (AuthSession activeSession : activeSessions) {
            activeSession.revoke("PASSWORD_CHANGED");
            authSessionRepository.save(activeSession);
        }
    }

    @Transactional
    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                UUID sessionId = tokenProvider.getSessionIdFromToken(token);
                if (sessionId != null) {
                    authSessionRepository.findById(sessionId).ifPresent(session -> {
                        if (session.isActive()) {
                            session.revoke("USER_LOGOUT");
                            authSessionRepository.save(session);
                        }
                    });
                }
            } catch (Exception ignored) {
            }

            try {
                java.util.Date expiry = tokenProvider.getExpirationFromToken(token);
                if (expiry != null) {
                    long remainingMillis = expiry.getTime() - System.currentTimeMillis();
                    if (remainingMillis > 0) {
                        tokenBlacklistService.blacklistToken(token, java.time.Duration.ofMillis(remainingMillis));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public AuthResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email().toLowerCase().trim(), request.password())
        );

        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();

        if (!userPrincipal.isEnabled()) {
            throw new DisabledException("Account is not active.");
        }
        if (!userPrincipal.isAccountNonLocked()) {
            throw new LockedException("Account is suspended.");
        }

        User user = userRepository.findByEmail(userPrincipal.getUsername())
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));

        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal.getUsername());
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);

        UUID sessionId = UUID.randomUUID();
        UUID tokenFamilyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusMillis(tokenProvider.getRefreshExpirationMs());

        String deviceLabel = parseDeviceLabel(userAgent);

        AuthSession session = new AuthSession(
            sessionId,
            user,
            tokenFamilyId,
            refreshTokenHash,
            ipAddress,
            userAgent,
            deviceLabel,
            expiresAt
        );
        authSessionRepository.save(session);

        String accessToken = tokenProvider.generateAccessToken(authentication, sessionId);

        return new AuthResponse(
            accessToken,
            refreshToken,
            tokenProvider.getExpirationMs(),
            UserDto.fromEntity(user)
        );
    }

    private String parseDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Device";
        }
        if (userAgent.length() > 100) {
            return userAgent.substring(0, 100);
        }
        return userAgent;
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!tokenProvider.validateToken(token)) {
            throw new BadCredentialsException("Invalid or expired refresh token.");
        }

        if (!tokenProvider.isRefreshToken(token)) {
            throw new BadCredentialsException("Supplied token is not a valid refresh token.");
        }

        String email = tokenProvider.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("User associated with token no longer exists."));

        String incomingHash = TokenHashUtil.sha256(token);

        AuthSession session = authSessionRepository.findByRefreshTokenHashWithLock(incomingHash)
            .orElseThrow(() -> new BadCredentialsException("Invalid or unrecognized refresh token."));

        // 1. Detect token reuse if this token was already rotated or revoked due to reuse
        if ("TOKEN_ROTATED".equals(session.getRevokeReason()) || "TOKEN_REUSE".equals(session.getRevokeReason())) {
            List<AuthSession> activeFamilySessions = authSessionRepository.findByTokenFamilyIdAndIsActiveTrue(session.getTokenFamilyId());
            for (AuthSession familySession : activeFamilySessions) {
                familySession.revoke("FAMILY_REVOKED");
                authSessionRepository.save(familySession);
            }
            throw new BadCredentialsException("Refresh token reuse detected. Token family has been revoked.");
        }

        // 2. Reject if revoked for any other reason (e.g. USER_LOGOUT, PASSWORD_CHANGED)
        if (!session.isActive() || session.getRevokedAt() != null) {
            throw new BadCredentialsException("Session has been revoked: " + session.getRevokeReason());
        }

        // 3. Reject if expired
        if (session.isExpired()) {
            session.revoke("EXPIRED");
            authSessionRepository.save(session);
            throw new BadCredentialsException("Session has expired.");
        }

        // 4. Verify account status
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            session.revoke("ACCOUNT_INACTIVE");
            authSessionRepository.save(session);
            throw new DisabledException("Account is not active.");
        }

        // 5. Rotate current session credential
        session.revoke("TOKEN_ROTATED");
        authSessionRepository.save(session);

        // 6. Generate and persist replacement session in the same token family
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getEmail());
        String newRefreshTokenHash = TokenHashUtil.sha256(newRefreshToken);

        UUID newSessionId = UUID.randomUUID();
        Instant newExpiresAt = Instant.now().plusMillis(tokenProvider.getRefreshExpirationMs());

        AuthSession newSession = new AuthSession(
            newSessionId,
            user,
            session.getTokenFamilyId(),
            newRefreshTokenHash,
            session.getIpAddress(),
            session.getUserAgent(),
            session.getDeviceLabel(),
            newExpiresAt
        );
        authSessionRepository.save(newSession);

        String newAccessToken = tokenProvider.generateAccessTokenFromEmail(
            user.getEmail(),
            user.getUserId(),
            user.getName(),
            newSessionId
        );

        return new AuthResponse(
            newAccessToken,
            newRefreshToken,
            tokenProvider.getExpirationMs(),
            UserDto.fromEntity(user)
        );
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        return UserDto.fromEntity(user);
    }
}
