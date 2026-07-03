package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.service.DistributedLockService;
import com.ticketing.inventory.service.InventoryService;
import com.ticketing.user.model.User;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Unit tests for ReservationExpirationJob — recovery of stale checkouts.
 *
 * Verifies that a PAYMENT_PENDING booking whose hold has lapsed is released back to inventory
 * and marked PAYMENT_FAILED, so it stops being a permanent dead-end.
 */
@ExtendWith(MockitoExtension.class)
class ReservationExpirationJobTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TicketTierRepository ticketTierRepository;
    @Mock private InventoryService inventoryService;
    @Mock private DistributedLockService lockService;
    @Mock private StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;

    @InjectMocks private ReservationExpirationJob job;

    @Test
    @DisplayName("expireReservations: a stale PAYMENT_PENDING booking releases seats and becomes PAYMENT_FAILED")
    void expire_whenPaymentPendingPastExpiry_shouldReleaseSeatsAndMarkFailed() {
        TicketTier tier = TicketTier.builder()
                .id(100L)
                .tierName("VIP")
                .basePrice(new BigDecimal("99.00"))
                .totalCapacity(100)
                .availableCount(40)
                .build();

        Booking booking = Booking.builder()
                .id(42L)
                .user(User.builder().id(1L).build())
                .state(BookingState.PAYMENT_PENDING)
                .totalAmount(new BigDecimal("99.00"))
                .expiresAt(Instant.now().minusSeconds(60))
                .tickets(new ArrayList<>())
                .build();
        booking.getTickets().add(Ticket.builder().id(1L).booking(booking).tier(tier).build());

        when(lockService.acquireLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(bookingRepository.findByStateInAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of(booking));
        when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));

        job.expireReservations();

        verify(inventoryService).releaseSeat(eq(100L), eq(1));
        assertThat(tier.getAvailableCount()).isEqualTo(41);
        assertThat(booking.getState()).isEqualTo(BookingState.PAYMENT_FAILED);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("expireReservations: an abandoned RESERVED hold releases seats and increments tier availableCount (Fix D19-1)")
    void expireReservedHold_shouldReleaseSeatsAndIncrementTierAvailableCount() {
        TicketTier tier = TicketTier.builder()
                .id(100L)
                .tierName("VIP")
                .basePrice(new BigDecimal("99.00"))
                .totalCapacity(100)
                .availableCount(40)
                .build();

        Booking booking = Booking.builder()
                .id(42L)
                .user(User.builder().id(1L).build())
                .state(BookingState.RESERVED)
                .totalAmount(new BigDecimal("99.00"))
                .expiresAt(Instant.now().minusSeconds(60))
                .tickets(new ArrayList<>())
                .build();
        booking.getTickets().add(Ticket.builder().id(1L).booking(booking).tier(tier).build());

        @SuppressWarnings("unchecked")
        StateMachine<BookingState, BookingEvent> stateMachine = mock(StateMachine.class);
        @SuppressWarnings("unchecked")
        StateMachineEventResult<BookingState, BookingEvent> result = mock(StateMachineEventResult.class);
        when(result.getResultType()).thenReturn(StateMachineEventResult.ResultType.ACCEPTED);
        when(stateMachine.startReactively()).thenReturn(Mono.empty());
        when(stateMachine.sendEvent(org.mockito.ArgumentMatchers.<Mono<org.springframework.messaging.Message<BookingEvent>>>any()))
                .thenReturn(Flux.just(result));
        when(stateMachineFactory.getStateMachine(anyString())).thenReturn(stateMachine);

        when(lockService.acquireLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(bookingRepository.findByStateInAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of(booking));
        when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));

        job.expireReservations();

        assertThat(booking.getState()).isEqualTo(BookingState.EXPIRED);
        verify(inventoryService).releaseSeat(eq(100L), eq(1));
        assertThat(tier.getAvailableCount()).isEqualTo(41);
        verify(ticketTierRepository).save(tier);
        verify(bookingRepository).save(booking);
    }
}
