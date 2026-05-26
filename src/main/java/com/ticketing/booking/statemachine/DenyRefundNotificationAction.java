package com.ticketing.booking.statemachine;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DenyRefundNotificationAction implements Action<BookingState, BookingEvent> {

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        log.info("Executing DenyRefundNotificationAction - notifying user of refund denial...");
        // Notification logic
    }
}
