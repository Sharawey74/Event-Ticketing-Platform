package com.ticketing.booking.statemachine;

import java.util.Map;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Guards the {@code CONFIRMED → ATTENDED} transition: only the organizer of the event a booking
 * belongs to (or an ADMIN) may check its attendee in.
 *
 * <p><b>Fix 26-checkin.</b> Fix 8.2 was recorded as applied, but only its wiring had landed — this
 * class existed, was declared on the transition, and was evaluated, while its body was a log line
 * and {@code return true}. Check-in therefore had a single authorization layer, the controller's
 * {@code @PreAuthorize} role check, rather than the two the fix described. Any {@code ORGANIZER}
 * could check in any confirmed booking, for any event, on any date. On a multi-organizer platform
 * that is a denial-of-entry attack on someone else's paying customers: a used ticket is refused at
 * the door.
 *
 * <p><b>This guard fails closed.</b> Every path that cannot positively establish ownership returns
 * {@code false}. That matters more than it looks: the previous behaviour was to default to
 * <em>allow</em>, and the state machine reports a denial by returning {@code DENIED} rather than
 * throwing, so a guard that silently permits is invisible from the outside.
 *
 * <p>Inputs arrive through the machine's <em>extended state</em>, not message headers — that is the
 * convention every Guard and Action in this package uses. {@code BookingService.checkIn} populates
 * them; before this fix it populated nothing at all, which is why the guard had no caller identity
 * available even in principle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInGuard implements Guard<BookingState, BookingEvent> {

    private final BookingRepository bookingRepository;

    @Override
    public boolean evaluate(StateContext<BookingState, BookingEvent> context) {
        Map<Object, Object> vars = context.getExtendedState().getVariables();

        Long bookingId = asLong(vars.get("bookingId"));
        Long currentUserId = asLong(vars.get("currentUserId"));
        boolean isAdmin = Boolean.TRUE.equals(vars.get("isAdmin"));

        if (bookingId == null || currentUserId == null) {
            log.warn("[guard] CheckInGuard denied: missing bookingId/currentUserId in extended state "
                    + "(bookingId={}, currentUserId={})", bookingId, currentUserId);
            return false;
        }

        // An ADMIN is already granted this endpoint by @PreAuthorize; the ownership rule is about
        // separating organizers from each other, not about restricting platform operators.
        if (isAdmin) {
            log.info("[guard] CheckInGuard allowed for booking {} — caller is ADMIN", bookingId);
            return true;
        }

        return bookingRepository.findById(bookingId)
                .map(booking -> {
                    if (booking.getEvent() == null || booking.getEvent().getOrganizer() == null) {
                        log.warn("[guard] CheckInGuard denied: booking {} has no resolvable organizer",
                                bookingId);
                        return false;
                    }
                    Long organizerId = booking.getEvent().getOrganizer().getId();
                    boolean owns = organizerId.equals(currentUserId);
                    if (!owns) {
                        log.warn("[guard] CheckInGuard denied: user {} does not organize event {} "
                                + "(organizer is {})", currentUserId, booking.getEvent().getId(), organizerId);
                    }
                    return owns;
                })
                .orElseGet(() -> {
                    log.warn("[guard] CheckInGuard denied: booking {} not found", bookingId);
                    return false;
                });
    }

    /** Extended-state values are untyped, so narrow defensively rather than casting blindly. */
    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
