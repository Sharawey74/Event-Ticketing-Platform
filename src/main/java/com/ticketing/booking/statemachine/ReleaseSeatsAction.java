package com.ticketing.booking.statemachine;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReleaseSeatsAction implements Action<BookingState, BookingEvent> {

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        log.info("Executing ReleaseSeatsAction - restoring inventory for booking...");
        // Logic to release seats back to InventoryService will be fully implemented when wired with BookingService
    }
}
