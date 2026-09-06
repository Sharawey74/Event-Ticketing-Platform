package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.statemachine.config.StateMachineFactory;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.service.DistributedLockService;
import com.ticketing.event.model.Event;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.inventory.service.InventoryService;
import com.ticketing.pricing.service.PricingEngine;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

/**
 * Fix 26-checkin — the service half of the check-in authorization fix.
 *
 * <p>The guard (see {@code CheckInGuardTest}) makes the state machine itself refuse a cross-organizer
 * check-in. But a guard can only answer yes or no, so a denial surfaces as a rejected transition and
 * would be reported as <b>409 Conflict</b> — the wrong answer for "this is not your event".
 *
 * <p>So ownership is also checked in the service, which throws {@link AccessDeniedException} and maps
 * to <b>403</b>. The two are not redundant: the service check gives callers the correct status, and
 * the guard keeps the state machine safe for any future caller that does not go through this method.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceCheckInTest {

    private static final Long BOOKING_ID = 500L;
    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ORGANIZER_ID = 99L;

    @Mock private BookingRepository bookingRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketTierRepository ticketTierRepository;
    @Mock private InventoryService inventoryService;
    @Mock private DistributedLockService lockService;
    @Mock private StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(OWNER_ID).email("owner@example.com").build();
        Event event = Event.builder().id(7L).title("Owned Event").organizer(owner).build();
        Booking booking = Booking.builder()
                .id(BOOKING_ID)
                .event(event)
                .state(BookingState.CONFIRMED)
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    @Test
    @DisplayName("checkIn: an organizer who does not own the event is refused with 403, not 409")
    void checkIn_whenCallerDoesNotOrganizeTheEvent_shouldThrowAccessDenied() {
        assertThatThrownBy(() -> bookingService.checkIn(BOOKING_ID, OTHER_ORGANIZER_ID, false))
                .isInstanceOf(AccessDeniedException.class);

        // Refused before the state machine is even acquired — no transition is attempted.
        verify(stateMachineFactory, never()).getStateMachine(any(String.class));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("checkIn: a booking that does not exist is a 404, not a silent no-op")
    void checkIn_whenBookingMissing_shouldThrowEntityNotFound() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.checkIn(404L, OWNER_ID, false))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    @DisplayName("checkIn: an ADMIN is not blocked by the organizer-ownership rule")
    void checkIn_whenCallerIsAdmin_shouldNotBeRefusedForOwnership() {
        // Reaches the state machine rather than being rejected up front. The machine is unstubbed,
        // so this fails later — the assertion is only that it is NOT an authorization failure.
        assertThatThrownBy(() -> bookingService.checkIn(BOOKING_ID, OTHER_ORGANIZER_ID, true))
                .isNotInstanceOf(AccessDeniedException.class);
    }
}
