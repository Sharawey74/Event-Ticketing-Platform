package com.ticketing.booking.statemachine;

import java.util.EnumSet;

import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
@SuppressWarnings("deprecation") // Phase 1B migration reminder: Move from StateMachineConfigurerAdapter to functional style
public class BookingStateMachineConfig extends StateMachineConfigurerAdapter<BookingState, BookingEvent> {

    private final Action<BookingState, BookingEvent> confirmBookingAction;
    private final Action<BookingState, BookingEvent> releaseSeatsAction;
    private final Action<BookingState, BookingEvent> denyRefundNotificationAction;
    private final Guard<BookingState, BookingEvent> checkInGuard;

    @Override
    public void configure(StateMachineConfigurationConfigurer<BookingState, BookingEvent> config) throws Exception {
        config
            .withConfiguration()
            .autoStartup(false)
            .listener(new StateMachineListenerAdapter<BookingState, BookingEvent>() {
                @Override
                public void stateChanged(State<BookingState, BookingEvent> from, State<BookingState, BookingEvent> to) {
                    log.info("State change: {} -> {}", from != null ? from.getId() : "none", to.getId());
                }
            });
    }

    @Override
    public void configure(StateMachineStateConfigurer<BookingState, BookingEvent> states) throws Exception {
        states
            .withStates()
            .initial(BookingState.RESERVED)
            .end(BookingState.EXPIRED)
            .end(BookingState.PAYMENT_FAILED)
            .end(BookingState.ATTENDED)
            .end(BookingState.REFUND_APPROVED)
            .end(BookingState.REFUND_DENIED)
            .end(BookingState.CANCELLED)
            .states(EnumSet.allOf(BookingState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<BookingState, BookingEvent> transitions) throws Exception {
        transitions
            .withExternal()
                .source(BookingState.RESERVED).target(BookingState.PAYMENT_PENDING).event(BookingEvent.PROCEED_TO_CHECKOUT)
                .and()
            .withExternal()
                .source(BookingState.PAYMENT_PENDING).target(BookingState.CONFIRMED).event(BookingEvent.PAYMENT_SUCCESS)
                .action(confirmBookingAction)
                .and()
            .withExternal()
                .source(BookingState.PAYMENT_PENDING).target(BookingState.PAYMENT_FAILED).event(BookingEvent.PAYMENT_FAILURE)
                .action(releaseSeatsAction)
                .and()
            .withExternal()
                .source(BookingState.RESERVED).target(BookingState.EXPIRED).event(BookingEvent.EXPIRE_RESERVATION)
                .action(releaseSeatsAction)
                .and()
            .withExternal()
                .source(BookingState.CONFIRMED).target(BookingState.REFUND_REQUESTED).event(BookingEvent.REQUEST_REFUND)
                .and()
            .withExternal()
                .source(BookingState.REFUND_REQUESTED).target(BookingState.REFUND_APPROVED).event(BookingEvent.APPROVE_REFUND)
                .action(releaseSeatsAction)
                .and()
            .withExternal()
                .source(BookingState.REFUND_REQUESTED).target(BookingState.REFUND_DENIED).event(BookingEvent.DENY_REFUND)
                .action(denyRefundNotificationAction)
                .and()
            .withExternal()
                .source(BookingState.CONFIRMED).target(BookingState.CANCELLED).event(BookingEvent.EVENT_CANCELLED)
                .and()
            .withExternal()
                .source(BookingState.RESERVED).target(BookingState.CANCELLED).event(BookingEvent.EVENT_CANCELLED)
                .and()
            .withExternal()
                .source(BookingState.PAYMENT_PENDING).target(BookingState.CANCELLED).event(BookingEvent.EVENT_CANCELLED)
                .and()
            .withExternal()
                .source(BookingState.CONFIRMED).target(BookingState.ATTENDED).event(BookingEvent.CHECK_IN)
                .guard(checkInGuard);
    }
}
