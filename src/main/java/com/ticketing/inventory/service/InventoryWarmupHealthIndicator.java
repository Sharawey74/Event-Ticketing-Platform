package com.ticketing.inventory.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class InventoryWarmupHealthIndicator implements HealthIndicator {

    private volatile boolean warmupComplete = false;

    public void markWarmupComplete() {
        this.warmupComplete = true;
    }

    @Override
    public Health health() {
        return warmupComplete
            ? Health.up().build()
            : Health.down().withDetail("reason", "Inventory warm-up in progress").build();
    }
}
