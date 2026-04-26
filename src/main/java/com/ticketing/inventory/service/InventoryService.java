package com.ticketing.inventory.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final StringRedisTemplate redisTemplate;
    private final InventoryWarmupHealthIndicator inventoryWarmupHealthIndicator;

    private static final String RESERVE_SEAT_LUA =
        "local count = redis.call('GET', KEYS[1]) " +
        "if count == false then return -2 end " +           // key doesn't exist
        "if tonumber(count) >= tonumber(ARGV[1]) then " +
        "  return redis.call('DECRBY', KEYS[1], ARGV[1]) " + // atomic decrement
        "else " +
        "  return -1 " +                                     // insufficient stock
        "end";

    @PostConstruct
    public void warmUpInventoryCache() {
        log.info("Starting inventory warm-up...");
        // In a real scenario, we would load all tier counts from TicketTierRepository here
        // e.g. ticketTierRepository.findAll().forEach(tier -> setAvailableCount(tier.getId(), tier.getAvailableCount()));
        
        inventoryWarmupHealthIndicator.markWarmupComplete();
        log.info("Inventory warm-up completed. Health indicator marked UP.");
    }

    private String getTierKey(Long tierId) {
        return "inventory:tier:" + tierId + ":available";
    }

    public int getAvailableCount(Long tierId) {
        String countStr = redisTemplate.opsForValue().get(getTierKey(tierId));
        return countStr != null ? Integer.parseInt(countStr) : 0;
    }

    public void setAvailableCount(Long tierId, int count) {
        redisTemplate.opsForValue().set(getTierKey(tierId), String.valueOf(count));
        log.info("Set available count for tier {} to {}", tierId, count);
    }

    public boolean reserveSeat(Long tierId, int quantity) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESERVE_SEAT_LUA, Long.class);
        Long result = redisTemplate.execute(
            script,
            Collections.singletonList(getTierKey(tierId)),
            String.valueOf(quantity)
        );

        if (result == null || result < 0) {
            log.warn("Failed to reserve {} seats for tier {}. Result: {}", quantity, tierId, result);
            return false; // -1 = insufficient stock, -2 = key missing
        }
        
        log.info("Reserved {} seats for tier {}. Remaining: {}", quantity, tierId, result);
        return true; // result = new count (>= 0)
    }

    public boolean releaseSeat(Long tierId, int quantity) {
        Long result = redisTemplate.opsForValue().increment(getTierKey(tierId), quantity);
        log.info("Released {} seats for tier {}. New count: {}", quantity, tierId, result);
        return result != null;
    }
}
