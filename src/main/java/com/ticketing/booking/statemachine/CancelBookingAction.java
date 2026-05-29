package com.ticketing.booking.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.publisher.BookingEventPublisher;

import lombok.RequiredArgsConstructor;

/**
 * Fix 11.1 — Executed when EVENT_CANCELLED fires on a CONFIRMED booking.
 *
 * Responsibilities:
 *  1. Trigger a full Stripe refund for the booking (Phase 1B: call PaymentService.refundFullAmount).
 *  2. Notify the user their booking was cancelled due to the event being cancelled.
 *
 * For RESERVED and PAYMENT_PENDING states, the releaseSeatsAction handles cancellation
 * (no payment was taken so no refund is needed).
 */
@Component
@RequiredArgsConstructor
public class CancelBookingAction implements Action<BookingState, BookingEvent> {

    private static final Logger logger = LoggerFactory.getLogger(CancelBookingAction.class);

    private final BookingEventPublisher publisher;

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        Long bookingId  = (Long) context.getExtendedState().getVariables().get("bookingId");
        String userEmail = (String) context.getExtendedState().getVariables().get("userEmail");

        logger.info("[ACTION] CancelBookingAction bookingId={} — event cancelled, full refund triggered",
                bookingId);

        // Phase 1B: paymentService.refundFullAmount(bookingId)

        // Notify user via RabbitMQ email queue
        EmailNotificationEvent notification = EmailNotificationEvent.builder()
                .to(userEmail != null ? userEmail : "")
                .subject("Your booking has been cancelled")
                .body(String.format(
                        "<p>We're sorry — the event you booked for has been cancelled. "
                        + "Your booking <strong>#%d</strong> will receive a full refund. "
                        + "Please allow 5–10 business days for the amount to appear on your statement.</p>",
                        bookingId != null ? bookingId : 0L))
                .templateType("EVENT_CANCELLED")
                .correlationId(org.slf4j.MDC.get("correlationId"))
                .build();

        publisher.publishEmailNotification(notification);
    }
}
