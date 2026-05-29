package com.ticketing.booking.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.waitlist.service.WaitlistService;

import lombok.RequiredArgsConstructor;

/**
 * State machine action executed when a booking moves to EXPIRED or REFUND_APPROVED.
 *
 * Responsibilities:
 *  1. Release seats back to the inventory (Redis counter via InventoryService — Phase 1B).
 *  2. Notify the top waitlist entry for the tier (Fix Day 11 — notifyWaitlist wired here).
 *
 * Fix Day 11: notifyWaitlist() is called AFTER seat release so inventory is already updated
 *             when the notified user clicks "book now".
 * Fix CC-1: correlationId is carried in MDC by the caller; WaitlistService reads it directly.
 */
@Component
@RequiredArgsConstructor
public class ReleaseSeatsAction implements Action<BookingState, BookingEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseSeatsAction.class);

    private final WaitlistService waitlistService;

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
        Long tierId    = (Long) context.getExtendedState().getVariables().get("tierId");
        Integer quantity = (Integer) context.getExtendedState().getVariables().get("quantity");

        logger.info("[ACTION] ReleaseSeatsAction bookingId={} tierId={} quantity={}",
                bookingId, tierId, quantity);

        // Phase 1B: call inventoryService.releaseSeat(tierId, quantity) here
        // Currently a stub — inventory release wired in BookingService on state transition.

        // Notify waitlist after inventory is released (Day 11 requirement)
        if (tierId != null && quantity != null && quantity > 0) {
            waitlistService.notifyWaitlist(tierId, quantity);
        }
    }
}
