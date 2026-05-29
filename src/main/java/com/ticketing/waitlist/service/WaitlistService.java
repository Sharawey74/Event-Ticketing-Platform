package com.ticketing.waitlist.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.inventory.service.InventoryService;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.publisher.BookingEventPublisher;
import com.ticketing.waitlist.model.WaitlistEntry;
import com.ticketing.waitlist.repository.WaitlistRepository;

import lombok.RequiredArgsConstructor;

/**
 * Manages the waitlist for ticket tiers that are fully sold out.
 *
 * Business rules:
 *   1. A user can only join the waitlist when inventory is 0.
 *   2. When a seat is released (EXPIRED or REFUND_APPROVED transition), the top N
 *      entries in the waitlist are notified via email (via RabbitMQ — Fix 10.2 pattern).
 *   3. ReleaseSeatsAction calls notifyWaitlist() after releasing inventory to Redis.
 *
 * Fix CC-1: correlationId is propagated into every email notification event.
 * Fix CC-2: no magic numbers — all thresholds in BusinessConstants.
 * Fix 11.2: AVAILABLE is NOT a booking state — waitlist is entirely based on
 *            InventoryService.getAvailableCount() (Redis) rather than any BookingState.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WaitlistService {

    private static final Logger logger = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final InventoryService inventoryService;
    private final BookingEventPublisher publisher;

    /**
     * Adds a user to the waitlist for a ticket tier.
     * Throws if inventory is still available — users must book directly while seats exist.
     *
     * @param userId  the user requesting to join the waitlist
     * @param tierId  the ticket tier they want to wait for
     * @param email   the user's email address (stored for denormalised notification)
     */
    @Transactional
    public void joinWaitlist(Long userId, Long tierId, String email) {
        int available = inventoryService.getAvailableCount(tierId);
        if (available > 0) {
            throw new IllegalStateException(
                    "Seats still available for tier " + tierId + " — join directly instead of waitlist.");
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .userId(userId)
                .tierId(tierId)
                .userEmail(email)
                .build();

        waitlistRepository.save(entry);
        logger.info("[WAITLIST] User {} joined waitlist for tier {} correlationId={}",
                userId, tierId, MDC.get("correlationId"));
    }

    /**
     * Overload used by tests — allows email to be omitted when testing inventory logic.
     * In production, always use joinWaitlist(userId, tierId, email).
     */
    @Transactional
    public void joinWaitlist(Long userId, Long tierId) {
        joinWaitlist(userId, tierId, "");
    }

    /**
     * Notifies the top N entries in the waitlist for a tier when seats become available.
     * Called from ReleaseSeatsAction after releasing inventory.
     *
     * @param tierId        the ticket tier that just had seats released
     * @param releasedSeats how many seats were released (= how many users to notify)
     */
    @Transactional
    public void notifyWaitlist(Long tierId, int releasedSeats) {
        if (releasedSeats <= 0) {
            return;
        }

        List<WaitlistEntry> entries = waitlistRepository
                .findTopByTierIdOrderByCreatedAtAsc(tierId, releasedSeats);

        if (entries.isEmpty()) {
            logger.debug("[WAITLIST] No waitlist entries for tier {}", tierId);
            return;
        }

        String correlationId = MDC.get("correlationId"); // Fix CC-1

        for (WaitlistEntry entry : entries) {
            EmailNotificationEvent notification = EmailNotificationEvent.builder()
                    .to(entry.getUserEmail())
                    .subject("A seat is now available — book now!")
                    .body("<p>Great news! A seat has just become available for the event you were "
                          + "waitlisted for. <a href=\"/events\">Book now</a> before it sells out again.</p>")
                    .templateType("WAITLIST_AVAILABLE")
                    .correlationId(correlationId)
                    .build();

            publisher.publishEmailNotification(notification);
            logger.info("[WAITLIST] Notified user {} for tier {} correlationId={}",
                    entry.getUserId(), tierId, correlationId);
        }
    }
}
