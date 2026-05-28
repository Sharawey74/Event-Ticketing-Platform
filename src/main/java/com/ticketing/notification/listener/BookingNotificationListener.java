package com.ticketing.notification.listener;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.repository.TicketRepository;
import com.ticketing.common.config.RabbitMQConfig;
import com.ticketing.notification.event.BookingConfirmedEvent;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.event.TicketGenerationEvent;
import com.ticketing.notification.publisher.BookingEventPublisher;
import com.ticketing.notification.service.EmailService;
import com.ticketing.notification.util.QRCodeGeneratorUtil;

import lombok.RequiredArgsConstructor;

/**
 * RabbitMQ message consumer for all booking notification flows.
 *
 * Three consumers:
 * 1. booking.confirmation.queue → handleBookingConfirmation()
 *    - Sends confirmation email
 *    - Publishes TicketGenerationEvent (Fix 10.2 — async QR, NOT in webhook handler)
 *
 * 2. email.notification.queue → handleEmailNotification()
 *    - Delegates to EmailService (one place for all email sending)
 *
 * 3. ticket.generation.queue → handleTicketGeneration()
 *    - Generates Base64 QR code via ZXing for each ticket (Fix 10.2)
 *    - Saves QR code to tickets.qr_code column
 *
 * Fix CC-1: X-Correlation-ID propagated via event.correlationId into MDC for each consumer.
 */
@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(BookingNotificationListener.class);

    private final BookingEventPublisher publisher;
    private final EmailService emailService;
    private final TicketRepository ticketRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Handler 1 — Booking Confirmation
    // ─────────────────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.BOOKING_CONFIRMATION_QUEUE)
    public void handleBookingConfirmation(BookingConfirmedEvent event) {
        // Fix CC-1 — restore correlationId into MDC for this consumer thread
        MDC.put("correlationId", event.getCorrelationId());
        try {
            logger.info("[LISTENER] bookingConfirmation bookingId={}", event.getBookingId());

            // 1. Send confirmation email
            String body = buildConfirmationEmailBody(event);
            emailService.sendEmail(event.getUserEmail(),
                    "Your booking is confirmed: " + event.getEventTitle(), body);

            // 2. Publish async QR generation (Fix 10.2 — NOT in Stripe webhook handler)
            if (event.getTicketIds() != null && !event.getTicketIds().isEmpty()) {
                TicketGenerationEvent ticketEvent = TicketGenerationEvent.builder()
                        .ticketIds(event.getTicketIds())
                        .bookingId(event.getBookingId())
                        .eventId(event.getEventId())
                        .correlationId(event.getCorrelationId())
                        .build();
                publisher.publishTicketGeneration(ticketEvent);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler 2 — Email Notification (generic)
    // ─────────────────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.EMAIL_NOTIFICATION_QUEUE)
    public void handleEmailNotification(EmailNotificationEvent event) {
        // Fix CC-1
        MDC.put("correlationId", event.getCorrelationId());
        try {
            logger.info("[LISTENER] emailNotification templateType={} to={}",
                    event.getTemplateType(), event.getTo());
            emailService.sendEmail(event.getTo(), event.getSubject(), event.getBody());
        } finally {
            MDC.remove("correlationId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler 3 — Ticket / QR Code Generation (Fix 10.2)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.TICKET_GENERATION_QUEUE)
    public void handleTicketGeneration(TicketGenerationEvent event) {
        // Fix CC-1
        MDC.put("correlationId", event.getCorrelationId());
        try {
            logger.info("[LISTENER] ticketGeneration bookingId={} ticketCount={}",
                    event.getBookingId(),
                    event.getTicketIds() != null ? event.getTicketIds().size() : 0);

            if (event.getTicketIds() == null || event.getTicketIds().isEmpty()) {
                logger.warn("[LISTENER] ticketGeneration called with empty ticketIds — skipping");
                return;
            }

            for (Long ticketId : event.getTicketIds()) {
                Optional<Ticket> optional = ticketRepository.findById(ticketId);
                if (optional.isEmpty()) {
                    logger.warn("[LISTENER] ticketGeneration ticket not found ticketId={} — skipping", ticketId);
                    continue;
                }
                Ticket ticket = optional.get();
                String qrContent = buildQrContent(ticket, event);
                String qrBase64 = QRCodeGeneratorUtil.generate(qrContent);
                ticketRepository.updateQrCode(ticketId, qrBase64);
                logger.info("[LISTENER] ticketGeneration QR saved ticketId={}", ticketId);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildConfirmationEmailBody(BookingConfirmedEvent event) {
        return String.format(
                "<h1>Booking Confirmed!</h1>"
                + "<p>Hi! Your booking for <strong>%s</strong> is confirmed.</p>"
                + "<p>Booking reference: <strong>#%d</strong></p>"
                + "<p>Total paid: <strong>%s</strong></p>",
                event.getEventTitle(),
                event.getBookingId(),
                event.getTotalAmount());
    }

    /**
     * Builds the QR code payload string for a ticket.
     * Content: JSON object with ticketId, bookingId, eventId, seatNumber.
     */
    private String buildQrContent(Ticket ticket, TicketGenerationEvent event) {
        return String.format(
                "{\"ticketId\":%d,\"bookingId\":%d,\"eventId\":%d,\"seatNumber\":\"%s\"}",
                ticket.getId(),
                event.getBookingId(),
                event.getEventId(),
                ticket.getSeatNumber() != null ? ticket.getSeatNumber() : "GA");
    }
}
