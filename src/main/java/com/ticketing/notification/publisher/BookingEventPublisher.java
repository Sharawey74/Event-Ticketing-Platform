package com.ticketing.notification.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.ticketing.common.config.RabbitMQConfig;
import com.ticketing.notification.event.BookingConfirmedEvent;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.event.TicketGenerationEvent;

import lombok.RequiredArgsConstructor;

/**
 * Publishes booking-related messages to RabbitMQ exchanges.
 *
 * Fix CC-1 — correlationId must be set on every event before calling these methods.
 *            The caller is responsible for reading MDC.get("correlationId") and setting
 *            event.setCorrelationId(correlationId).
 */
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes a booking confirmation event to the booking exchange.
     * Consumed by BookingNotificationListener.handleBookingConfirmation().
     */
    public void publishBookingConfirmation(BookingConfirmedEvent event) {
        logger.info("[PUBLISH] bookingId={} correlationId={} routing=booking.confirmed",
                event.getBookingId(), event.getCorrelationId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE, "booking.confirmed", event);
    }

    /**
     * Publishes an email notification to the notification exchange.
     * Consumed by BookingNotificationListener.handleEmailNotification().
     */
    public void publishEmailNotification(EmailNotificationEvent event) {
        logger.info("[PUBLISH] templateType={} to={} correlationId={} routing=email.send",
                event.getTemplateType(), event.getTo(), event.getCorrelationId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE, "email.send", event);
    }

    /**
     * Publishes a ticket generation event. Fix 10.2 — QR codes generated ASYNC here,
     * never in the webhook handler.
     * Consumed by BookingNotificationListener.handleTicketGeneration().
     */
    public void publishTicketGeneration(TicketGenerationEvent event) {
        logger.info("[PUBLISH] bookingId={} ticketCount={} correlationId={} routing=ticket.generate",
                event.getBookingId(),
                event.getTicketIds() != null ? event.getTicketIds().size() : 0,
                event.getCorrelationId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE, "ticket.generate", event);
    }
}
