package com.ticketing.booking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.service.DistributedLockService;
import com.ticketing.common.util.BusinessConstants;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.inventory.service.InventoryService;
import com.ticketing.pricing.service.PricingEngine;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;
import org.springframework.statemachine.config.StateMachineFactory;

import com.ticketing.common.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TicketTierRepository ticketTierRepository;
    private final InventoryService inventoryService;
    private final DistributedLockService lockService;
    private final StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;
    private final PricingEngine pricingEngine;

    @Transactional
    public Booking reserveTickets(Long userId, Long eventId, Long tierId, int quantity) {
        if (quantity <= 0 || quantity > BusinessConstants.MAX_TICKETS_PER_BOOKING) {
            throw new IllegalArgumentException("Invalid ticket quantity. Max allowed: " + BusinessConstants.MAX_TICKETS_PER_BOOKING);
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Event is not open for booking");
        }

        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket tier not found: " + tierId));

        if (!tier.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("Ticket tier does not belong to the given event");
        }

        // 1. Check availability outside lock
        int available = inventoryService.getAvailableCount(tierId);
        if (available < quantity) {
            throw new IllegalStateException("Not enough tickets available");
        }

        String lockKey = "tier:" + tierId + ":user:" + userId;
        String lockValue = UUID.randomUUID().toString();

        if (!lockService.acquireLock(lockKey, lockValue, BusinessConstants.RESERVATION_TTL_SECONDS)) {
            throw new IllegalStateException("Could not acquire lock, please try again");
        }

        try {
            // Fix 8.1: Check availability AGAIN inside the lock (TOCTOU guard)
            available = inventoryService.getAvailableCount(tierId);
            if (available < quantity) {
                throw new IllegalStateException("Not enough tickets available");
            }

            // Reserve in Redis inventory (Lua floor guard)
            boolean reserved = inventoryService.reserveSeat(tierId, quantity);
            if (!reserved) {
                throw new IllegalStateException("Failed to reserve seats in inventory");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

            // PricingEngine integration (Day 12): calculate the final per-ticket price
            // using the authoritative available count from inside the lock.
            // Order of rules: earlyBird → group discount → dynamic surge (all from BusinessConstants).
            int sold = tier.getTotalCapacity() - available;
            BigDecimal pricePerTicket = pricingEngine.calculateFinalPrice(
                    tier.getBasePrice(),
                    event.getStartDate(),
                    quantity,
                    sold,
                    tier.getTotalCapacity()
            );
            BigDecimal totalAmount = pricePerTicket.multiply(BigDecimal.valueOf(quantity));

            Booking booking = Booking.builder()
                    .user(user)
                    .event(event)
                    .state(BookingState.RESERVED)
                    .totalAmount(totalAmount)
                    .expiresAt(Instant.now().plusSeconds(BusinessConstants.RESERVATION_TTL_SECONDS))
                    .build();

            for (int i = 0; i < quantity; i++) {
                Ticket ticket = Ticket.builder()
                        .booking(booking)
                        .tier(tier)
                        .checkInStatus(false)
                        .build();
                booking.getTickets().add(ticket);
            }

            log.info("Successfully reserved {} tickets for user {} on tier {}", quantity, userId, tierId);
            return bookingRepository.save(booking);
        } finally {
            lockService.releaseLock(lockKey, lockValue);
        }
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not own this booking");
        }

        // RESERVED = abandon an unpaid hold. PAYMENT_PENDING = abandon a checkout the user
        // started but never completed (recovery from a stuck Stripe session). Both release seats.
        if (booking.getState() != BookingState.RESERVED
                && booking.getState() != BookingState.PAYMENT_PENDING) {
            throw new ConflictException(
                    "Only RESERVED or PAYMENT_PENDING bookings can be self-cancelled. Current state: "
                            + booking.getState());
        }

        int quantity = booking.getTickets().size();
        Long tierId = booking.getTickets().get(0).getTier().getId();

        inventoryService.releaseSeat(tierId, quantity);

        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket tier not found: " + tierId));
        tier.setAvailableCount(tier.getAvailableCount() + quantity);
        ticketTierRepository.save(tier);

        booking.setState(BookingState.CANCELLED);
        bookingRepository.save(booking);

        log.info("[booking] Booking {} self-cancelled by user {}. Released {} seats for tier {}",
                bookingId, userId, quantity, tierId);
    }

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public void checkIn(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        
        var stateMachine = stateMachineFactory.getStateMachine(Long.toString(bookingId));
        stateMachine.startReactively().block();
        
        var result = stateMachine.sendEvent(reactor.core.publisher.Mono.just(
                org.springframework.messaging.support.MessageBuilder.withPayload(BookingEvent.CHECK_IN).build()
        )).blockLast();

        if (result == null || result.getResultType().name().equals("DENIED")) {
            throw new IllegalStateException("Check-in denied by guard or invalid state for booking " + bookingId);
        }
        
        booking.setState(BookingState.ATTENDED);
        // Additional ticket-level check-in logic would go here
        bookingRepository.save(booking);
    }
}
