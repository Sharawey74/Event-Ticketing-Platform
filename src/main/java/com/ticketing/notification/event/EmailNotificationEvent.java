package com.ticketing.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message payload published to notification.exchange / routing key: email.send
 * Consumed by BookingNotificationListener.handleEmailNotification()
 * Covers: booking confirmations, refund denials, cancellations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationEvent {

    /**
     * Recipient email address
     */
    private String to;
    private String subject;

    /**
     * HTML body — may include inline Base64 QR codes
     */
    private String body;

    /**
     * Logical template type used for logging and future templating.
     * Examples: BOOKING_CONFIRMED, REFUND_DENIED, EVENT_CANCELLED
     */
    private String templateType;

    private String correlationId; // Fix CC-1
}
