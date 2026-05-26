package com.ticketing.common.service;

import java.time.Duration;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String RELEASE_LOCK_LUA =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    /**
     * Attempts to acquire a distributed lock.
     * @param key the lock key
     * @param value the unique value for this lock owner (e.g., UUID or Thread ID)
     * @param ttlSeconds the time-to-live in seconds
     * @return true if acquired, false otherwise
     */
    public boolean acquireLock(String key, String value, long ttlSeconds) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
        if (Boolean.TRUE.equals(success)) {
            log.debug("Acquired lock '{}' with value '{}' for {} seconds", key, value, ttlSeconds);
            return true;
        }
        return false;
    }

    /**
     * Releases a distributed lock using a Lua script to ensure ownership check.
     * @param key the lock key
     * @param value the unique value for this lock owner
     * @return true if released, false if the lock was not held by this owner or already expired
     */
    public boolean releaseLock(String key, String value) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_LUA, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), value);
        
        boolean released = result != null && result > 0;
        if (released) {
            log.debug("Released lock '{}' with value '{}'", key, value);
        } else {
            log.debug("Failed to release lock '{}' with value '{}' (may have expired or owned by another)", key, value);
        }
        return released;
    }
}
