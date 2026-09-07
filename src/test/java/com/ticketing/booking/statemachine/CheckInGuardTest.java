package com.ticketing.booking.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
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
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateContext;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.event.model.Event;
import com.ticketing.user.model.User;

/**
 * Fix 26-checkin — the guard on {@code CONFIRMED → ATTENDED}.
 *
 * <p>Fix 8.2 was recorded as applied, but only its wiring landed: the guard existed, was declared
 * on the transition, and was evaluated — while its body was a log line and {@code return true}.
 * Check-in therefore had one authorization layer (the controller's role check), not the two the
 * fix claimed. Any {@code ORGANIZER} could check in any confirmed booking for any event, which on
 * a multi-organizer platform means marking a rival's attendees as arrived and having them refused
 * at the door.
 *
 * <p>These tests define what the guard must actually decide.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInGuardTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ORGANIZER_ID = 99L;
    private static final Long BOOKING_ID = 500L;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private StateContext<com.ticketing.booking.model.BookingState,
            com.ticketing.booking.model.BookingEvent> context;

    @Mock
    private ExtendedState extendedState;

    @InjectMocks
    private CheckInGuard checkInGuard;

    private Map<Object, Object> variables;

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        when(context.getExtendedState()).thenReturn(extendedState);
        when(extendedState.getVariables()).thenReturn(variables);

        User owner = User.builder().id(OWNER_ID).email("owner@example.com").build();
        Event event = Event.builder().id(7L).title("Owned Event").organizer(owner).build();
        Booking booking = Booking.builder().id(BOOKING_ID).event(event).build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    @Test
    @DisplayName("allows check-in when the caller organizes the event the booking belongs to")
    void evaluate_whenCallerOrganizesTheEvent_shouldAllow() {
        variables.put("bookingId", BOOKING_ID);
        variables.put("currentUserId", OWNER_ID);
        variables.put("isAdmin", false);

        assertThat(checkInGuard.evaluate(context)).isTrue();
    }

    @Test
    @DisplayName("denies check-in when a different organizer tries to check in someone else's attendee")
    void evaluate_whenCallerIsADifferentOrganizer_shouldDeny() {
        variables.put("bookingId", BOOKING_ID);
        variables.put("currentUserId", OTHER_ORGANIZER_ID);
        variables.put("isAdmin", false);

        // The core of the fix: passing the controller's role check is not enough.
        assertThat(checkInGuard.evaluate(context)).isFalse();
    }

    @Test
    @DisplayName("allows an ADMIN to check in any booking — the controller already grants them the endpoint")
    void evaluate_whenCallerIsAdmin_shouldAllow() {
        variables.put("bookingId", BOOKING_ID);
        variables.put("currentUserId", OTHER_ORGANIZER_ID);
        variables.put("isAdmin", true);

        assertThat(checkInGuard.evaluate(context)).isTrue();
    }

    @Test
    @DisplayName("fails closed when the extended state carries no caller — never default to allow")
    void evaluate_whenCurrentUserIdMissing_shouldDeny() {
        variables.put("bookingId", BOOKING_ID);
        // currentUserId deliberately absent, as it was for every call before this fix

        assertThat(checkInGuard.evaluate(context)).isFalse();
    }

    @Test
    @DisplayName("fails closed when the booking id is missing")
    void evaluate_whenBookingIdMissing_shouldDeny() {
        variables.put("currentUserId", OWNER_ID);

        assertThat(checkInGuard.evaluate(context)).isFalse();
    }

    @Test
    @DisplayName("fails closed when the booking does not exist")
    void evaluate_whenBookingNotFound_shouldDeny() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());
        variables.put("bookingId", 404L);
        variables.put("currentUserId", OWNER_ID);

        assertThat(checkInGuard.evaluate(context)).isFalse();
    }
}
