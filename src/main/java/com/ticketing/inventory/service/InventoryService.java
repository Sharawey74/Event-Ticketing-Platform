package com.ticketing.inventory.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.ticketing.booking.repository.TicketTierRepository;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final StringRedisTemplate redisTemplate;
    private final InventoryWarmupHealthIndicator inventoryWarmupHealthIndicator;
    // Fix A.1: Inject TicketTierRepository so the warm-up can load real data from the DB
    private final TicketTierRepository ticketTierRepository;

    private static final String RESERVE_SEAT_LUA =
        "local count = redis.call('GET', KEYS[1]) " +
        "if count == false then return -2 end " +           // key doesn't exist
        "if tonumber(count) >= tonumber(ARGV[1]) then " +
        "  return redis.call('DECRBY', KEYS[1], ARGV[1]) " + // atomic decrement
        "else " +
        "  return -1 " +                                     // insufficient stock
        "end";

    /**
     * Fix A.1: Loads ALL ticket tier available counts from the DB into Redis on startup.
     *
     * WHY THIS MATTERS: Between the moment Spring starts accepting HTTP requests and the
     * moment this @PostConstruct completes, any call to reserveSeat() would find no Redis
     * key and return -2 (key missing), causing ALL seat reservations to fail with an error.
     * This warm-up loop eliminates that window by populating Redis before marking the app
     * as ready (InventoryWarmupHealthIndicator.markWarmupComplete()).
     *
     * CROSS-DOMAIN NOTE: InventoryService uses TicketTierRepository from the booking domain.
     * This is a deliberate pragmatic choice for the warm-up path. If this domain is ever
     * extracted to a microservice, replace this with a REST call to the booking service.
     */
    @PostConstruct
    public void warmUpInventoryCache() {
        log.info("Inventory warm-up started — loading tier counts from database into Redis...");

        List<com.ticketing.booking.model.TicketTier> allTiers = ticketTierRepository.findAll();
        int loadedCount = 0;

        for (com.ticketing.booking.model.TicketTier tier : allTiers) {
            try {
                setAvailableCount(tier.getId(), tier.getAvailableCount());
                loadedCount++;
            } catch (Exception e) {
                log.error("Failed to warm up inventory for tier {} (name: {}). Continuing with remaining tiers.",
                    tier.getId(), tier.getTierName(), e);
            }
        }

        inventoryWarmupHealthIndicator.markWarmupComplete();
        log.info("Inventory warm-up completed. Loaded {}/{} tiers into Redis. Health indicator marked UP.",
            loadedCount, allTiers.size());
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
