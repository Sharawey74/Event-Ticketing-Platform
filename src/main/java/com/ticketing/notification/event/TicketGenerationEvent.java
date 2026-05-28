package com.ticketing.notification.event;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message payload published to booking.exchange / routing key: ticket.generate
 * Fix 10.2 — QR code generation is ASYNC: this event is published by
 * BookingNotificationListener.handleBookingConfirmation() and consumed by
 * BookingNotificationListener.handleTicketGeneration(), NOT by the Stripe webhook handler.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGenerationEvent {

    private List<Long> ticketIds;
    private Long bookingId;
    private Long eventId;
    private String correlationId; // Fix CC-1
}
