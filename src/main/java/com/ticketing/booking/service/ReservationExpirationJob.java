package com.ticketing.booking.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.service.DistributedLockService;
import com.ticketing.common.util.BusinessConstants;
import com.ticketing.inventory.service.InventoryService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationExpirationJob {

    private final BookingRepository bookingRepository;
    private final TicketTierRepository ticketTierRepository;
    private final InventoryService inventoryService;
    private final DistributedLockService lockService;
    private final StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;

    private static final String LOCK_KEY = "job:reservation_expiration";
    private final String lockValue = UUID.randomUUID().toString(); // Instance specific lock value

    @Scheduled(fixedRate = BusinessConstants.EXPIRY_JOB_INTERVAL_MS)
    @Transactional
    public void expireReservations() {
        // Fix 8.3: Distributed lock for the expiration job
        if (!lockService.acquireLock(LOCK_KEY, lockValue, 25)) {
            log.debug("Another instance is running the expiration job. Skipping...");
            return;
        }

        try {
            // Recovery: expire both unpaid holds (RESERVED) and abandoned checkouts (PAYMENT_PENDING).
            // PAYMENT_PENDING bookings have their hold extended to the Stripe session lifetime, so by
            // the time they appear here the Stripe session has also expired — no race with live payments.
            List<Booking> expiredBookings = bookingRepository.findByStateInAndExpiresAtBefore(
                    List.of(BookingState.RESERVED, BookingState.PAYMENT_PENDING), Instant.now());
            if (expiredBookings.isEmpty()) {
                return;
            }

            log.info("Found {} expired reservations to process", expiredBookings.size());

            for (Booking booking : expiredBookings) {
                try {
                    if (booking.getState() == BookingState.PAYMENT_PENDING) {
                        expireStalePaymentPending(booking);
                    } else {
                        expireReservedHold(booking);
                    }
                } catch (Exception e) {
                    log.error("Error expiring booking {}", booking.getId(), e);
                }
            }
        } finally {
            lockService.releaseLock(LOCK_KEY, lockValue);
        }
    }

    /**
     * RESERVED hold timed out before checkout — drive it through the state machine to EXPIRED.
     *
     * Fix D19-1: {@code ReleaseSeatsAction} (fired by this transition) is a Redis-release stub
     * only (Phase 1B), so the seat release and DB-side {@code availableCount} increment are done
     * explicitly here — mirroring {@link #expireStalePaymentPending(Booking)} and
     * {@code BookingService.cancelBooking} — to keep every release path symmetric with the
     * decrement now applied in {@code BookingService.reserveTickets}.
     */
    private void expireReservedHold(Booking booking) {
        var stateMachine = stateMachineFactory.getStateMachine(Long.toString(booking.getId()));
        stateMachine.startReactively().block();

        var result = stateMachine.sendEvent(Mono.just(
                MessageBuilder.withPayload(BookingEvent.EXPIRE_RESERVATION).build())).blockLast();

        if (result != null && result.getResultType().name().equals("ACCEPTED")) {
            booking.setState(BookingState.EXPIRED);

            if (!booking.getTickets().isEmpty()) {
                int quantity = booking.getTickets().size();
                Long tierId = booking.getTickets().get(0).getTier().getId();

                inventoryService.releaseSeat(tierId, quantity);

                TicketTier tier = ticketTierRepository.findById(tierId)
                        .orElseThrow(() -> new EntityNotFoundException("Ticket tier not found: " + tierId));
                tier.setAvailableCount(tier.getAvailableCount() + quantity);
                ticketTierRepository.save(tier);
            }

            bookingRepository.save(booking);
            log.info("Successfully expired booking {}", booking.getId());
        } else {
            log.warn("Failed to expire booking {}", booking.getId());
        }
    }

    /**
     * Checkout was started but never completed within the Stripe session window. Release the held
     * seats back to inventory and mark the booking PAYMENT_FAILED so it is no longer a dead-end.
     * Mirrors {@code BookingService.cancelBooking} so self-cancel and auto-expire free seats identically.
     */
    private void expireStalePaymentPending(Booking booking) {
        if (!booking.getTickets().isEmpty()) {
            int quantity = booking.getTickets().size();
            Long tierId = booking.getTickets().get(0).getTier().getId();

            inventoryService.releaseSeat(tierId, quantity);

            TicketTier tier = ticketTierRepository.findById(tierId)
                    .orElseThrow(() -> new EntityNotFoundException("Ticket tier not found: " + tierId));
            tier.setAvailableCount(tier.getAvailableCount() + quantity);
            ticketTierRepository.save(tier);

            log.info("Released {} seats for tier {} from stale PAYMENT_PENDING booking {}",
                    quantity, tierId, booking.getId());
        }

        booking.setState(BookingState.PAYMENT_FAILED);
        bookingRepository.save(booking);
        log.info("Stale PAYMENT_PENDING booking {} marked PAYMENT_FAILED", booking.getId());
    }
}
