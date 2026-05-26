package com.ticketing.booking.service;

import java.time.Instant;
import java.util.UUID;

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
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;
import org.springframework.statemachine.config.StateMachineFactory;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

            Booking booking = Booking.builder()
                    .user(user)
                    .event(event)
                    .state(BookingState.RESERVED)
                    .totalAmount(tier.getBasePrice().multiply(java.math.BigDecimal.valueOf(quantity)))
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
