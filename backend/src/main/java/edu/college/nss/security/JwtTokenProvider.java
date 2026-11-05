package edu.college.nss.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-ms:900000}") long expirationMs,
        @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
    ) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be configured and at least 32 characters (256 bits) long.");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT access token expiration must be greater than zero.");
        }
        if (refreshExpirationMs <= 0) {
            throw new IllegalArgumentException("JWT refresh token expiration must be greater than zero.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Deprecated
    public String generateAccessToken(Authentication authentication) {
        throw new UnsupportedOperationException("Access tokens require an explicit persistent sessionId binding.");
    }

    public String generateAccessToken(Authentication authentication, UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Access tokens require a persistent sessionId binding.");
        }
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        if (userPrincipal.getUserId() == null) {
            throw new IllegalArgumentException("Access tokens require an authenticated user with a non-null userId.");
        }
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(userPrincipal.getUsername())
            .claim("type", "ACCESS")
            .claim("userId", userPrincipal.getUserId().toString())
            .claim("name", userPrincipal.getName())
            .claim("sessionId", sessionId.toString())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact();
    }

    @Deprecated
    public String generateAccessTokenFromEmail(String email, UUID userId, String name) {
        throw new UnsupportedOperationException("Access tokens require an explicit persistent sessionId binding.");
    }

    public String generateAccessTokenFromEmail(String email, UUID userId, String name, UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Access tokens require a persistent sessionId binding.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Access tokens require an explicit non-null userId.");
        }
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(email)
            .claim("type", "ACCESS")
            .claim("userId", userId.toString())
            .claim("name", name)
            .claim("sessionId", sessionId.toString())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(email)
            .claim("type", "REFRESH")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return claims.getSubject();
    }

    public Date getIssuedAtFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return claims.getIssuedAt();
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return claims.getExpiration();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return "ACCESS".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return "REFRESH".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public UUID getSessionIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String sid = claims.get("sessionId", String.class);
            return sid != null ? UUID.fromString(sid) : null;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public UUID getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String uid = claims.get("userId", String.class);
            return uid != null ? UUID.fromString(uid) : null;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
