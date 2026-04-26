package com.ticketing.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class InventoryServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:4-management").withExposedPorts(5672);

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getFirstMappedPort);
    }

    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("reserveSeat: should decrement count when sufficient inventory")
    void reserveSeat_whenSufficientInventory_shouldDecrementCount() {
        // Arrange
        Long tierId = 1L;
        inventoryService.setAvailableCount(tierId, 10);

        // Act
        boolean result = inventoryService.reserveSeat(tierId, 2);

        // Assert
        assertThat(result).isTrue();
        assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(8);
    }

    @Test
    @DisplayName("reserveSeat: should return false when available count is 0 (floor guard)")
    void reserveSeat_whenInsufficientInventory_shouldReturnFalse() {
        // Arrange
        Long tierId = 2L;
        inventoryService.setAvailableCount(tierId, 0);

        // Act
        boolean result = inventoryService.reserveSeat(tierId, 1);

        // Assert
        assertThat(result).isFalse();
        assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(0);
    }

    @Test
    @DisplayName("releaseSeat: should increment count")
    void releaseSeat_shouldIncrementCount() {
        // Arrange
        Long tierId = 3L;
        inventoryService.setAvailableCount(tierId, 5);

        // Act
        boolean result = inventoryService.releaseSeat(tierId, 3);

        // Assert
        assertThat(result).isTrue();
        assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(8);
    }

    @Test
    @DisplayName("reserveSeat: 100 concurrent threads on 50-seat tier -> exactly 50 succeed")
    void concurrentReservations_100threads_50seats_exactlyFiftySucceed() throws InterruptedException {
        // Arrange
        Long tierId = 4L;
        inventoryService.setAvailableCount(tierId, 50);
        ExecutorService executor = Executors.newFixedThreadPool(100);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(100);

        // Act
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    if (inventoryService.reserveSeat(tierId, 1)) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);

        // Assert
        assertThat(successes.get()).isEqualTo(50);
        assertThat(failures.get()).isEqualTo(50);
        assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(0);
    }
}
