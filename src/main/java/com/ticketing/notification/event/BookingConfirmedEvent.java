package com.ticketing.notification.event;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message payload published to booking.exchange / routing key: booking.confirmed
 * Consumed by BookingNotificationListener.handleBookingConfirmation()
 * Triggers: email notification + async QR generation (Fix 10.2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {

    private Long bookingId;
    private Long userId;
    private String userEmail;
    private Long eventId;
    private String eventTitle;
    private List<Long> ticketIds;
    private BigDecimal totalAmount;
    private String correlationId; // Fix CC-1 — propagated from HTTP MDC
}
