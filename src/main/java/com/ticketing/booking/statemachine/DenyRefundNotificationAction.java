package com.ticketing.booking.statemachine;

import org.slf4j.MDC;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.publisher.BookingEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fix 10.1 — SSM action executed on the REFUND_REQUESTED → REFUND_DENIED transition.
 *
 * Publishes an EmailNotificationEvent to notify the user their refund was denied.
 * Without this action, the user receives zero feedback when a refund is denied.
 *
 * Flow: DENY_REFUND event fires → this action runs → EmailNotificationEvent published →
 *       BookingNotificationListener.handleEmailNotification() sends the email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DenyRefundNotificationAction implements Action<BookingState, BookingEvent> {

    private final BookingEventPublisher publisher;

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
        String reason  = (String) context.getExtendedState().getVariables().get("denialReason");
        String userEmail = (String) context.getExtendedState().getVariables().get("userEmail");

        log.info("[ACTION] DenyRefundNotificationAction bookingId={} reason={}", bookingId, reason);

        String body = buildDenialEmailBody(bookingId, reason);

        EmailNotificationEvent event = EmailNotificationEvent.builder()
                .to(userEmail != null ? userEmail : "")
                .subject("Your refund request has been denied")
                .body(body)
                .templateType("REFUND_DENIED")
                .correlationId(MDC.get("correlationId")) // Fix CC-1
                .build();

        publisher.publishEmailNotification(event);
    }

    private String buildDenialEmailBody(Long bookingId, String reason) {
        String reasonText = (reason != null && !reason.isBlank()) ? reason : "see our refund policy for details";
        return String.format(
                "<p>We're sorry, your refund request for booking <strong>#%d</strong> "
                + "has been denied.</p><p>Reason: %s</p>",
                bookingId != null ? bookingId : 0L,
                reasonText);
    }
}
