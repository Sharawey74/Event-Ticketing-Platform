package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.exception.ConflictException;
import com.ticketing.event.model.Event;
import com.ticketing.inventory.service.InventoryService;
import com.ticketing.user.model.User;

/**
 * Unit tests for BookingService.cancelBooking — recovery behaviour.
 *
 * Verifies that a user can self-cancel both a RESERVED hold and a stuck PAYMENT_PENDING
 * checkout, and that seats are released to inventory in both cases.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TicketTierRepository ticketTierRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks private BookingService bookingService;

    private User owner;
    private TicketTier tier;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).email("owner@test.com").build();

        Event event = Event.builder().id(10L).title("Concert").build();

        tier = TicketTier.builder()
                .id(100L)
                .tierName("VIP")
                .basePrice(new BigDecimal("99.00"))
                .event(event)
                .totalCapacity(100)
                .availableCount(40)
                .build();
    }

    private Booking bookingInState(BookingState state) {
        Booking booking = Booking.builder()
                .id(42L)
                .user(owner)
                .state(state)
                .totalAmount(new BigDecimal("99.00"))
                .tickets(new ArrayList<>())
                .build();
        booking.getTickets().add(Ticket.builder().id(1L).booking(booking).tier(tier).build());
        return booking;
    }

    @Test
    @DisplayName("cancelBooking: a stuck PAYMENT_PENDING booking is cancelled and its seats released")
    void cancelBooking_whenPaymentPending_shouldReleaseSeatsAndCancel() {
        Booking booking = bookingInState(BookingState.PAYMENT_PENDING);
        when(bookingRepository.findByIdWithDetails(42L)).thenReturn(Optional.of(booking));
        when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));

        bookingService.cancelBooking(42L, 1L);

        assertThat(booking.getState()).isEqualTo(BookingState.CANCELLED);
        verify(inventoryService).releaseSeat(eq(100L), eq(1));
        assertThat(tier.getAvailableCount()).isEqualTo(41);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("cancelBooking: a RESERVED hold is still cancellable and releases seats")
    void cancelBooking_whenReserved_shouldReleaseSeatsAndCancel() {
        Booking booking = bookingInState(BookingState.RESERVED);
        when(bookingRepository.findByIdWithDetails(42L)).thenReturn(Optional.of(booking));
        when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));

        bookingService.cancelBooking(42L, 1L);

        assertThat(booking.getState()).isEqualTo(BookingState.CANCELLED);
        verify(inventoryService).releaseSeat(eq(100L), eq(1));
    }

    @Test
    @DisplayName("cancelBooking: a CONFIRMED booking cannot be self-cancelled")
    void cancelBooking_whenConfirmed_shouldThrowConflict() {
        Booking booking = bookingInState(BookingState.CONFIRMED);
        when(bookingRepository.findByIdWithDetails(42L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(42L, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PAYMENT_PENDING");

        verify(inventoryService, never()).releaseSeat(anyLong(), anyInt());
    }
}
