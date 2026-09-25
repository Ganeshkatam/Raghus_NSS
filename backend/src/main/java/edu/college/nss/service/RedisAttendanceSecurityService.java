package edu.college.nss.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisAttendanceSecurityService {

    private static final Logger log = LoggerFactory.getLogger(RedisAttendanceSecurityService.class);
    private static final String LOCK_PREFIX = "nss:attendance:lock:";

    private final StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    public RedisAttendanceSecurityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Acquires an atomic check-in concurrency lock for a volunteer within an attendance session.
     * Prevents race conditions, replay attacks, and duplicate simultaneous submissions.
     *
     * @return true if lock was acquired, false if a check-in is already in progress
     */
    public boolean acquireCheckInLock(UUID sessionId, UUID volunteerId) {
        if (redisTemplate == null) {
            return true; // Gracefully proceed if Redis is not configured
        }
        try {
            String lockKey = LOCK_PREFIX + sessionId + ":" + volunteerId;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(15));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            log.warn("Redis attendance lock failed, falling back to database constraints: {}", ex.getMessage());
            return true;
        }
    }

    /**
     * Releases the attendance check-in lock upon successful completion or handled error.
     */
    public void releaseCheckInLock(UUID sessionId, UUID volunteerId) {
        if (redisTemplate == null) return;
        try {
            String lockKey = LOCK_PREFIX + sessionId + ":" + volunteerId;
            redisTemplate.delete(lockKey);
        } catch (Exception ignored) {
        }
    }
}
