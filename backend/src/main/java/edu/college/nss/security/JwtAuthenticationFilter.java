package edu.college.nss.security;

import edu.college.nss.domain.AuthSession;
import edu.college.nss.repository.AuthSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RedisTokenBlacklistService tokenBlacklistService;
    private final AuthSessionRepository authSessionRepository;

    public JwtAuthenticationFilter(
        JwtTokenProvider tokenProvider,
        CustomUserDetailsService userDetailsService,
        RedisTokenBlacklistService tokenBlacklistService,
        AuthSessionRepository authSessionRepository
    ) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.authSessionRepository = authSessionRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt)) {
            // 1. Signature, structure, expiration, and strict ACCESS type claim check (P0.1)
            if (!tokenProvider.validateToken(jwt) || !tokenProvider.isAccessToken(jwt)) {
                log.debug("Bearer token rejected: invalid, expired, or non-ACCESS token");
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Reject legacy or malformed access tokens missing persistent sessionId binding (P0.2)
            UUID sessionId = tokenProvider.getSessionIdFromToken(jwt);
            if (sessionId == null) {
                log.warn("Access token rejected: missing required sessionId claim");
                filterChain.doFilter(request, response);
                return;
            }

            // 3. Fast-path optimization: Redis blacklist check
            try {
                if (tokenBlacklistService != null && tokenBlacklistService.isTokenBlacklisted(jwt)) {
                    log.debug("Token rejected by Redis blacklist");
                    filterChain.doFilter(request, response);
                    return;
                }
            } catch (Exception ex) {
                log.warn("Redis blacklist check encountered error; falling back to persistent session check: {}", ex.getMessage());
            }

            // 4. Authoritative check: persistent PostgreSQL session record (P0.2)
            AuthSession session;
            try {
                Optional<AuthSession> sessionOpt = authSessionRepository.findByIdWithUser(sessionId);
                if (sessionOpt.isEmpty()) {
                    log.debug("Session {} not found in database", sessionId);
                    filterChain.doFilter(request, response);
                    return;
                }
                session = sessionOpt.get();
            } catch (DataAccessException ex) {
                log.error("Database error verifying session {}: {}", sessionId, ex.getMessage());
                sendServiceUnavailableResponse(response, "SESSION_STORE_UNAVAILABLE", "Session verification service is temporarily unavailable.");
                return;
            }

            // 5. Verify persistent session validity: active, unrevoked, and unexpired (P0.2)
            if (!session.isActive() || session.getRevokedAt() != null || session.isExpired()) {
                log.debug("Session {} is inactive, revoked (reason={}), or expired", sessionId, session.getRevokeReason());
                filterChain.doFilter(request, response);
                return;
            }

            // 6. User account verification: token email must match session user email, and account must be ACTIVE
            String email = tokenProvider.getEmailFromToken(jwt);
            if (email == null || session.getUser() == null || !session.getUser().getEmail().equalsIgnoreCase(email)) {
                log.warn("Token subject does not match persistent session user for sessionId {}", sessionId);
                filterChain.doFilter(request, response);
                return;
            }

            if (!"ACTIVE".equalsIgnoreCase(session.getUser().getStatus())) {
                log.debug("User {} account status is not ACTIVE (status={})", email, session.getUser().getStatus());
                filterChain.doFilter(request, response);
                return;
            }

            // 7. Load UserDetails and authenticate
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("User account for {} is disabled or locked", email);
                }
            } catch (DataAccessException ex) {
                log.error("Database error loading user details for {}: {}", email, ex.getMessage());
                sendServiceUnavailableResponse(response, "USER_STORE_UNAVAILABLE", "Authentication service is temporarily unavailable.");
                return;
            } catch (Exception ex) {
                log.warn("Failed to load user details for {}: {}", email, ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendServiceUnavailableResponse(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format("{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}", code, message));
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
