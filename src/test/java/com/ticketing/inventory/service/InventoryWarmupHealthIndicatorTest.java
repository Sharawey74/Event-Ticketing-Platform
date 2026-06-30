package com.ticketing.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class InventoryWarmupHealthIndicatorTest {

    private InventoryWarmupHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new InventoryWarmupHealthIndicator();
    }

    @Test
    @DisplayName("health: should return DOWN before markWarmupComplete is called")
    void health_beforeWarmup_shouldReturnDown() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
        assertThat(health.getDetails().get("reason")).isEqualTo("Inventory warm-up in progress");
    }

    @Test
    @DisplayName("health: should return UP after markWarmupComplete is called")
    void health_afterWarmupComplete_shouldReturnUp() {
        indicator.markWarmupComplete();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
    }
}
