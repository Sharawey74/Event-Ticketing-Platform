package com.ticketing.booking.statemachine;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralised State Machine acquisition layer.
 *
 * <p>Responsibility: Provide a fully prepared {@link StateMachine} instance for a given
 * booking ID — one that already has the {@link BookingStateChangeInterceptor} attached.
 *
 * <p>This eliminates the architectural risk where every caller of
 * {@code StateMachineFactory.getStateMachine()} had to remember to manually attach the
 * interceptor. If they forgot, the state machine would transition internally but the DB
 * booking row would never be updated (silent state desync).
 *
 * <p>Usage: Inject this service wherever a state machine is needed instead of injecting
 * {@code StateMachineFactory} directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStateMachineService {

    private final StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;
    private final BookingStateChangeInterceptor bookingStateChangeInterceptor;

    /**
     * Returns a state machine instance for the given booking, with:
     * <ol>
     *   <li>The {@link BookingStateChangeInterceptor} attached globally to all regions</li>
     *   <li>The internal state reset to {@code CONFIRMED} so refund transitions work correctly</li>
     * </ol>
     *
     * <p>The interceptor is registered <em>before</em> the reset so it fires on every
     * subsequent transition — including the CONFIRMED → REFUND_REQUESTED first hop.
     *
     * @param bookingId the booking whose state machine instance to acquire
     * @param userEmail the booking owner's email, stored in extended state for notification actions
     * @param denialReason optional denial reason string; {@code null} for approval paths
     * @return a started, interceptor-wired state machine ready to accept refund events
     */
    public StateMachine<BookingState, BookingEvent> acquireForRefund(
            Long bookingId, String userEmail, String denialReason) {

        StateMachine<BookingState, BookingEvent> sm =
                stateMachineFactory.getStateMachine(Long.toString(bookingId));

        // Step 1: Attach interceptor + reset to CONFIRMED — interceptor first so it fires
        // during the reset transition to CONFIRMED and all subsequent transitions.
        sm.getStateMachineAccessor().doWithAllRegions(access -> {
            access.addStateMachineInterceptor(bookingStateChangeInterceptor);
            access.resetStateMachineReactively(
                    new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                            BookingState.CONFIRMED, null, null, null)).block();
        });

        // Step 2: Populate extended state variables needed by Actions
        sm.getExtendedState().getVariables().put("bookingId", bookingId);
        sm.getExtendedState().getVariables().put("userEmail", userEmail);
        if (denialReason != null) {
            sm.getExtendedState().getVariables().put("denialReason", denialReason);
        }

        // Step 3: Start the machine
        sm.startReactively().block();

        log.debug("[SM] Acquired state machine for booking {} with interceptor attached", bookingId);
        return sm;
    }
}
