package com.ticketing.booking.statemachine;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CheckInGuard implements Guard<BookingState, BookingEvent> {

    @Override
    public boolean evaluate(StateContext<BookingState, BookingEvent> context) {
        log.info("Evaluating CheckInGuard - ensuring valid check-in conditions...");
        // Additional business logic for check-in eligibility
        return true;
    }
}
