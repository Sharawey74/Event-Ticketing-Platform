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
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.common.service.DistributedLockService;
import com.ticketing.common.util.BusinessConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationExpirationJob {

    private final BookingRepository bookingRepository;
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
            List<Booking> expiredBookings = bookingRepository.findByStateAndExpiresAtBefore(BookingState.RESERVED, Instant.now());
            if (expiredBookings.isEmpty()) {
                return;
            }

            log.info("Found {} expired reservations to process", expiredBookings.size());

            for (Booking booking : expiredBookings) {
                try {
                    var stateMachine = stateMachineFactory.getStateMachine(Long.toString(booking.getId()));
                    stateMachine.startReactively().block();
                    
                    var result = stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.EXPIRE_RESERVATION).build())).blockLast();
                    
                    if (result != null && result.getResultType().name().equals("ACCEPTED")) {
                        // Normally the action would handle DB update, but just in case we sync it
                        booking.setState(BookingState.EXPIRED);
                        bookingRepository.save(booking);
                        log.info("Successfully expired booking {}", booking.getId());
                    } else {
                        log.warn("Failed to expire booking {}", booking.getId());
                    }
                } catch (Exception e) {
                    log.error("Error expiring booking {}", booking.getId(), e);
                }
            }
        } finally {
            lockService.releaseLock(LOCK_KEY, lockValue);
        }
    }
}
