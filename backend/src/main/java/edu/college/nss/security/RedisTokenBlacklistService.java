package edu.college.nss.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class RedisTokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);
    private static final String TOKEN_BLACKLIST_PREFIX = "nss:blacklist:token:";
    private static final String USER_REVOCATION_PREFIX = "nss:blacklist:user:";

    private final StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    public RedisTokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklistToken(String token, Duration ttl) {
        if (redisTemplate == null || token == null || token.isBlank()) {
            return;
        }
        try {
            String hash = hashToken(token);
            long seconds = Math.max(ttl.getSeconds(), 60);
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + hash, "revoked", Duration.ofSeconds(seconds));
            log.info("Blacklisted token in Redis for {} seconds", seconds);
        } catch (Exception ex) {
            log.warn("Failed to blacklist token in Redis: {}", ex.getMessage());
        }
    }

    public boolean isTokenBlacklisted(String token) {
        if (redisTemplate == null || token == null || token.isBlank()) {
            return false;
        }
        try {
            String hash = hashToken(token);
            Boolean exists = redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + hash);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.warn("Redis blacklist check failed, allowing token: {}", ex.getMessage());
            return false;
        }
    }

    public void blacklistAllUserTokens(String email) {
        if (redisTemplate == null || email == null || email.isBlank()) {
            return;
        }
        try {
            String key = USER_REVOCATION_PREFIX + email.toLowerCase().trim();
            redisTemplate.opsForValue().set(key, String.valueOf(Instant.now().toEpochMilli()), Duration.ofDays(7));
            log.info("Revoked all sessions in Redis for user {}", email);
        } catch (Exception ex) {
            log.warn("Failed to set user revocation in Redis: {}", ex.getMessage());
        }
    }

    public boolean isUserRevokedBefore(String email, Instant issuedAt) {
        if (redisTemplate == null || email == null || issuedAt == null) {
            return false;
        }
        try {
            String key = USER_REVOCATION_PREFIX + email.toLowerCase().trim();
            String timestampStr = redisTemplate.opsForValue().get(key);
            if (timestampStr != null) {
                long revokedAtEpoch = Long.parseLong(timestampStr);
                return issuedAt.toEpochMilli() < revokedAtEpoch;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(token.hashCode());
        }
    }
}
