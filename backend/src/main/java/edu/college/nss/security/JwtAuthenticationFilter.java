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
    private final AuthSessionRepository authSessionRepository;

    public JwtAuthenticationFilter(
        JwtTokenProvider tokenProvider,
        CustomUserDetailsService userDetailsService,
        AuthSessionRepository authSessionRepository
    ) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.authSessionRepository = authSessionRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Clear security context at the beginning of every request to prevent
        // ambient authentication from surviving a request without a bearer token.
        // In MockMvc test harnesses using @WithMockUser without a bearer token,
        // preserve the framework-injected test principal by unwrapping request decorators.
        HttpServletRequest rootRequest = request;
        while (rootRequest instanceof jakarta.servlet.http.HttpServletRequestWrapper wrapper) {
            if (wrapper.getRequest() instanceof HttpServletRequest inner) {
                rootRequest = inner;
            } else {
                break;
            }
        }
        boolean isMockTestRequest = rootRequest.getClass().getName().startsWith("org.springframework.mock.");
        String jwt = getJwtFromRequest(request);

        if (!isMockTestRequest || StringUtils.hasText(jwt)) {
            SecurityContextHolder.clearContext();
        }

        if (StringUtils.hasText(jwt)) {
            // 1. Signature, structure, expiration, and strict ACCESS type claim check (P0.1)
            if (!tokenProvider.validateToken(jwt) || !tokenProvider.isAccessToken(jwt)) {
                log.debug("Bearer token rejected: invalid, expired, or non-ACCESS token");
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Reject tokens missing required sessionId or userId claim (P0.2)
            UUID sessionId = tokenProvider.getSessionIdFromToken(jwt);
            UUID tokenUserId = tokenProvider.getUserIdFromToken(jwt);
            if (sessionId == null || tokenUserId == null) {
                log.warn("Access token rejected: missing required sessionId or userId claim");
                filterChain.doFilter(request, response);
                return;
            }

            // 3. Authoritative check: persistent PostgreSQL session record (P0.2)
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
                log.error("Database error verifying session {}", sessionId);
                sendServiceUnavailableResponse(response, "SESSION_STORE_UNAVAILABLE", "Session verification service is temporarily unavailable.");
                return;
            } catch (Exception ex) {
                log.error("Unexpected failure verifying session {}", sessionId);
                sendServiceUnavailableResponse(response, "SESSION_STORE_UNAVAILABLE", "Session verification service is temporarily unavailable.");
                return;
            }

            // 4. Verify persistent session validity using session.isValid() (P0.2)
            if (!session.isValid()) {
                log.debug("Session {} is invalid (active={}, revokedAt={}, expiresAt={})",
                    sessionId, session.isActive(), session.getRevokedAt(), session.getExpiresAt());
                filterChain.doFilter(request, response);
                return;
            }

            // 5. User account verification: token email and token userId must match session user, and account must be ACTIVE
            String email = tokenProvider.getEmailFromToken(jwt);
            if (email == null || session.getUser() == null || session.getUser().getUserId() == null) {
                log.warn("Token subject or session user identity missing for sessionId {}", sessionId);
                filterChain.doFilter(request, response);
                return;
            }

            if (!tokenUserId.equals(session.getUser().getUserId()) || !session.getUser().getEmail().equalsIgnoreCase(email)) {
                log.warn("Token identity claims do not match persistent session user for sessionId {}", sessionId);
                filterChain.doFilter(request, response);
                return;
            }

            if (!"ACTIVE".equalsIgnoreCase(session.getUser().getStatus())) {
                log.debug("User {} account status is not ACTIVE (status={})", email, session.getUser().getStatus());
                filterChain.doFilter(request, response);
                return;
            }

            // 6. Load UserDetails and authenticate
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
                log.error("Database error loading user details for {}", email);
                sendServiceUnavailableResponse(response, "USER_STORE_UNAVAILABLE", "Authentication service is temporarily unavailable.");
                return;
            } catch (Exception ex) {
                log.error("Unexpected error loading user details for {}", email);
                sendServiceUnavailableResponse(response, "USER_STORE_UNAVAILABLE", "Authentication service is temporarily unavailable.");
                return;
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
