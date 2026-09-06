package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.common.exception.ConflictException;
import com.ticketing.user.model.User;

/**
 * Fix 26-idem — unit tests for the idempotency wrapper around booking creation.
 *
 * <p>Before this, {@code Idempotency-Key} was a doorman who checked you had a ticket without ever
 * reading it: {@code RateLimitFilter} rejected a blank header and did nothing else with the value.
 * A client-side timeout on a request that actually succeeded, a second browser tab, or an automatic
 * network retry could therefore create two bookings — and a second Stripe charge. That is the shape
 * of the failure that charged booking 562 twice.
 *
 * <p>The guarantee under test: <b>one key, at most one booking</b>. Replaying a key returns the
 * booking it already created rather than making another.
 */
@ExtendWith(MockitoExtension.class)
class BookingIdempotencyServiceTest {

    private static final String KEY = "3f1a9c2e-0d4b-4a77-9c1e-8b2d5f6a7c30";

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingIdempotencyService bookingIdempotencyService;

    private User owner;
    private Booking existingBooking;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).email("owner@example.com").build();

        existingBooking = Booking.builder()
                .id(500L)
                .user(owner)
                .state(BookingState.RESERVED)
                .idempotencyKey(KEY)
                .build();
    }

    @Test
    @DisplayName("reserveTickets: a key never seen before delegates to BookingService and passes the key through")
    void reserveTickets_whenKeyIsNew_shouldDelegateAndForwardTheKey() {
        when(bookingRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(bookingService.reserveTickets(1L, 10L, 100L, 2, KEY)).thenReturn(existingBooking);

        Booking result = bookingIdempotencyService.reserveTickets(KEY, 1L, 10L, 100L, 2);

        assertThat(result).isSameAs(existingBooking);
        verify(bookingService).reserveTickets(1L, 10L, 100L, 2, KEY);
    }

    @Test
    @DisplayName("reserveTickets: replaying a key returns the original booking WITHOUT reserving again")
    void reserveTickets_whenKeyAlreadyUsedBySameUser_shouldReturnExistingBookingAndNotReserve() {
        when(bookingRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existingBooking));

        Booking result = bookingIdempotencyService.reserveTickets(KEY, 1L, 10L, 100L, 2);

        assertThat(result).isSameAs(existingBooking);
        assertThat(result.getId()).isEqualTo(500L);

        // The whole point: no second reservation, so no second hold and no second Stripe charge.
        verify(bookingService, never()).reserveTickets(anyLong(), anyLong(), anyLong(), anyInt());
        verify(bookingService, never()).reserveTickets(anyLong(), anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("reserveTickets: a concurrent duplicate that loses the UNIQUE race returns the winner's booking")
    void reserveTickets_whenConcurrentDuplicateHitsUniqueConstraint_shouldReturnExistingBooking() {
        // Both requests pass the fast-path lookup (neither has committed yet), then the loser's
        // INSERT hits uq_bookings_idempotency_key. Postgres blocks the second inserter until the
        // first commits, so by the time the violation is raised the winner's row is visible.
        when(bookingRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())               // fast path — not there yet
                .thenReturn(Optional.of(existingBooking));  // after the violation — winner committed

        when(bookingService.reserveTickets(1L, 10L, 100L, 2, KEY))
                .thenThrow(new DataIntegrityViolationException("uq_bookings_idempotency_key"));

        Booking result = bookingIdempotencyService.reserveTickets(KEY, 1L, 10L, 100L, 2);

        assertThat(result).isSameAs(existingBooking);
    }

    @Test
    @DisplayName("reserveTickets: a violation with no matching key is a real constraint bug and must NOT be swallowed")
    void reserveTickets_whenViolationIsNotTheIdempotencyConstraint_shouldRethrow() {
        when(bookingRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(bookingService.reserveTickets(1L, 10L, 100L, 2, KEY))
                .thenThrow(new DataIntegrityViolationException("some other constraint"));

        assertThatThrownBy(() -> bookingIdempotencyService.reserveTickets(KEY, 1L, 10L, 100L, 2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("some other constraint");
    }

    @Test
    @DisplayName("reserveTickets: another user's key is a conflict — never return someone else's booking")
    void reserveTickets_whenKeyBelongsToAnotherUser_shouldThrowConflictException() {
        when(bookingRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existingBooking));

        // User 2 replays user 1's key.
        assertThatThrownBy(() -> bookingIdempotencyService.reserveTickets(KEY, 2L, 10L, 100L, 2))
                .isInstanceOf(ConflictException.class);

        verify(bookingService, never()).reserveTickets(anyLong(), anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("reserveTickets: a null key delegates straight through — no lookup, behaviour unchanged")
    void reserveTickets_whenKeyIsNull_shouldDelegateWithoutIdempotencyLookup() {
        when(bookingService.reserveTickets(1L, 10L, 100L, 2)).thenReturn(existingBooking);

        Booking result = bookingIdempotencyService.reserveTickets(null, 1L, 10L, 100L, 2);

        assertThat(result).isSameAs(existingBooking);
        verify(bookingRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    @DisplayName("reserveTickets: a blank key is treated as absent, not as a usable key")
    void reserveTickets_whenKeyIsBlank_shouldDelegateWithoutIdempotencyLookup() {
        when(bookingService.reserveTickets(1L, 10L, 100L, 2)).thenReturn(existingBooking);

        Booking result = bookingIdempotencyService.reserveTickets("   ", 1L, 10L, 100L, 2);

        assertThat(result).isSameAs(existingBooking);
        verify(bookingRepository, never()).findByIdempotencyKey(anyString());
    }
}
