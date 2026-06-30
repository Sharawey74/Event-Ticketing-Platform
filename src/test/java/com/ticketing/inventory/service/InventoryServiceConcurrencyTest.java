package com.ticketing.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketing.ticketing_platform.TestcontainersConfiguration;

/**
 * Fix 16.1 — Lua floor guard concurrency test.
 *
 * Dedicated class with a startLatch so all 100 threads fire simultaneously.
 * NO @Transactional on the class: threads spawned by ExecutorService do not
 * share the test's transaction context (BUG-D16-1 fix).
 */
@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
class InventoryServiceConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    private static final Long TIER_ID = 9001L;

    @BeforeEach
    void setUp() {
        inventoryService.setAvailableCount(TIER_ID, 50);
    }

    @AfterEach
    void tearDown() {
        inventoryService.setAvailableCount(TIER_ID, 0);
    }

    @Test
    @DisplayName("Fix 16.1: Lua floor guard — 100 threads / 50 seats → exactly 50 succeed, count never negative")
    void reserveSeat_whenConcurrentRequests_shouldNeverGoBelowZero() throws InterruptedException {
        int seatCount   = 50;
        int threadCount = 100;

        ExecutorService executor   = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch  = new CountDownLatch(1);      // fires all threads simultaneously
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();   // block until all threads are ready
                    if (inventoryService.reserveSeat(TIER_ID, 1)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();   // -1 = floor guard, -2 = key missing
                    }
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();                   // release all 100 threads at once
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(seatCount);
        assertThat(failCount.get()).isEqualTo(threadCount - seatCount);
        assertThat(inventoryService.getAvailableCount(TIER_ID)).isEqualTo(0);
        assertThat(inventoryService.getAvailableCount(TIER_ID)).isGreaterThanOrEqualTo(0);
    }
}
