package com.ticketing.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private DistributedLockService distributedLockService;

    @Test
    @DisplayName("acquireLock: should return true when Redis key is not held")
    void acquireLock_whenKeyAvailable_shouldReturnTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("lock:test"), eq("owner-1"), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        boolean acquired = distributedLockService.acquireLock("lock:test", "owner-1", 30L);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("lock:test", "owner-1", Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("acquireLock: should return false when Redis key is already held")
    void acquireLock_whenKeyAlreadyLocked_shouldReturnFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        boolean acquired = distributedLockService.acquireLock("lock:test", "owner-2", 30L);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("acquireLock: should return false when Redis returns null (connection issue)")
    void acquireLock_whenRedisReturnsNull_shouldReturnFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(null);

        boolean acquired = distributedLockService.acquireLock("lock:test", "owner-3", 30L);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("releaseLock: should return true when Lua script confirms ownership and deletes key")
    void releaseLock_whenOwnerReleases_shouldReturnTrue() {
        when(redisTemplate.execute(any(DefaultRedisScript.class),
                eq(Collections.singletonList("lock:test")), eq("owner-1")))
                .thenReturn(1L);

        boolean released = distributedLockService.releaseLock("lock:test", "owner-1");

        assertThat(released).isTrue();
    }

    @Test
    @DisplayName("releaseLock: should return false when Lua script denies release (wrong owner)")
    void releaseLock_whenNotOwner_shouldReturnFalse() {
        when(redisTemplate.execute(any(DefaultRedisScript.class),
                eq(Collections.singletonList("lock:test")), eq("wrong-owner")))
                .thenReturn(0L);

        boolean released = distributedLockService.releaseLock("lock:test", "wrong-owner");

        assertThat(released).isFalse();
    }

    @Test
    @DisplayName("releaseLock: should return false when Lua script returns null")
    void releaseLock_whenRedisReturnsNull_shouldReturnFalse() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                .thenReturn(null);

        boolean released = distributedLockService.releaseLock("lock:test", "owner-1");

        assertThat(released).isFalse();
    }
}
