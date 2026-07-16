package com.ticketing.payment.service;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.PaymentStatus;
import com.ticketing.payment.model.ProcessedStripeEvent;
import com.ticketing.payment.repository.PaymentRepository;
import com.ticketing.payment.repository.ProcessedStripeEventRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes incoming Stripe webhook events.
 *
 * Fix 9.1 — CRITICAL: This service is @Transactional. The HTTP 200 is returned by
 * StripeWebhookController ONLY AFTER this method returns (i.e., AFTER the transaction commits).
 * The controller itself is NOT @Transactional — this separation is intentional.
 *
 * Fix 9.2 — CRITICAL: Idempotency is enforced by the UNIQUE constraint on
 * processed_stripe_events.stripe_event_id, not by a pre-check. Two concurrent deliveries
 * of the same event will race to INSERT — the second will throw DataIntegrityViolationException,
 * which we catch here and silently return. This is the correct, race-condition-safe guard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WebhookService {

    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final com.ticketing.notification.publisher.BookingEventPublisher bookingEventPublisher;

    @Value("${stripe.webhook-secret:#{null}}")
    private String webhookSecret;

    /**
     * Verifies the Stripe signature and delegates to processEvent().
     * Called by StripeWebhookController — this is the entry point for all webhooks.
     *
     * @throws SignatureVerificationException if the signature is invalid (controller returns 400)
     */
    @Transactional
    public void verifyAndProcess(String payload, String sigHeader) throws SignatureVerificationException {
        String correlationId = MDC.get("correlationId");
        log.info("[{}] [webhook] Received Stripe webhook. Verifying signature.", correlationId);

        // Verify signature — throws SignatureVerificationException on failure
        com.stripe.model.Event stripeEvent = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        log.info("[{}] [webhook] Signature valid. Event type: {} id: {}", correlationId,
                stripeEvent.getType(), stripeEvent.getId());

        processEvent(stripeEvent);
    }

    /**
     * Core processing logic — @Transactional so HTTP 200 is sent only after commit (Fix 9.1).
     * Public for direct testing in WebhookServiceTest.
     */
    @Transactional
    public void processEvent(com.stripe.model.Event stripeEvent) {
        String correlationId = MDC.get("correlationId");

        // Fix 9.2: Attempt to INSERT the event ID. If the UNIQUE constraint fires,
        // DataIntegrityViolationException is thrown — caught below. This is the hard guard.
        try {
            processedStripeEventRepository.save(
                    ProcessedStripeEvent.builder()
                            .stripeEventId(stripeEvent.getId())
                            .processedAt(Instant.now())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // Duplicate delivery — already processed. Silently return. (Fix 9.2)
            log.info("[{}] [webhook] Duplicate Stripe event ignored: {}", correlationId, stripeEvent.getId());
            return;
        }

        // Route by event type
        switch (stripeEvent.getType()) {
            case "checkout.session.completed" -> handlePaymentSuccess(stripeEvent, correlationId);
            case "checkout.session.expired"   -> handlePaymentExpired(stripeEvent, correlationId);
            default -> log.info("[{}] [webhook] Unhandled Stripe event type: {}", correlationId, stripeEvent.getType());
        }
    }

    private void handlePaymentSuccess(com.stripe.model.Event stripeEvent, String correlationId) {
        Session session = deserializeSession(stripeEvent);
        if (session == null) return;

        String bookingIdStr = session.getMetadata().get("bookingId");
        if (bookingIdStr == null) {
            log.error("[{}] [webhook] checkout.session.completed missing bookingId in metadata. Session: {}",
                    correlationId, session.getId());
            return;
        }

        Long bookingId = Long.parseLong(bookingIdStr);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (booking.getState() != BookingState.PAYMENT_PENDING) {
            log.warn("[{}] [webhook] Booking {} in unexpected state {} for payment success. Skipping.",
                    correlationId, bookingId, booking.getState());
            return;
        }

        // Transition booking to CONFIRMED
        booking.setState(BookingState.CONFIRMED);
        bookingRepository.save(booking);

        // Update Payment record to COMPLETED
        paymentRepository.findByStripeSessionId(session.getId()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setStripePaymentIntentId(session.getPaymentIntent());
            paymentRepository.save(payment);
        });

        log.info("[{}] [webhook] Booking {} confirmed after successful payment. Session: {}",
                correlationId, bookingId, session.getId());

        // Publish event to trigger async email notification and QR code generation (Fix: previously missing!)
        java.util.List<Long> ticketIds = booking.getTickets().stream()
                .map(com.ticketing.booking.model.Ticket::getId)
                .toList();

        com.ticketing.notification.event.BookingConfirmedEvent confirmationEvent = 
                com.ticketing.notification.event.BookingConfirmedEvent.builder()
                .bookingId(booking.getId())
                .userId(booking.getUser().getId())
                .userEmail(booking.getUser().getEmail())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .totalAmount(booking.getTotalAmount())
                .ticketIds(ticketIds)
                .correlationId(correlationId)
                .build();
        
        // Fix (Bug 2): a RabbitMQ publish failure must never roll back the CONFIRMED state
        // or the idempotency insert above — both already reflect a genuine successful payment.
        // Swallowing here means this booking's ticket-generation/email event is not retried
        // automatically; a durable outbox/retry pattern would close that gap but is out of
        // scope for this fix.
        try {
            bookingEventPublisher.publishBookingConfirmation(confirmationEvent);
        } catch (Exception ex) {
            log.error("[{}] [webhook] Failed to publish BookingConfirmedEvent for booking {} — "
                    + "booking remains CONFIRMED, but async ticket generation/email will not fire "
                    + "for this event", correlationId, booking.getId(), ex);
        }
    }

    private void handlePaymentExpired(com.stripe.model.Event stripeEvent, String correlationId) {
        Session session = deserializeSession(stripeEvent);
        if (session == null) return;

        String bookingIdStr = session.getMetadata() != null ? session.getMetadata().get("bookingId") : null;
        if (bookingIdStr == null) return;

        Long bookingId = Long.parseLong(bookingIdStr);
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            if (booking.getState() == BookingState.PAYMENT_PENDING) {
                booking.setState(BookingState.PAYMENT_FAILED);
                bookingRepository.save(booking);
                log.info("[{}] [webhook] Booking {} marked PAYMENT_FAILED. Session expired: {}",
                        correlationId, bookingId, session.getId());
            }
        });
    }

    private Session deserializeSession(com.stripe.model.Event stripeEvent) {
        EventDataObjectDeserializer deserializer = stripeEvent.getDataObjectDeserializer();

        // Primary: standard deserialization (works when library version matches event API version)
        Optional<com.stripe.model.StripeObject> optional = deserializer.getObject();
        if (optional.isPresent() && optional.get() instanceof Session session) {
            return session;
        }

        // Fallback: use Stripe's native GSON deserializer on the raw JSON.
        // This flawlessly handles API version mismatches between the CLI and the SDK without throwing.
        try {
            String rawJson = deserializer.getRawJson();
            if (rawJson != null && !rawJson.trim().isEmpty()) {
                Session session = com.stripe.net.ApiResource.GSON.fromJson(rawJson, Session.class);
                if (session != null && session.getId() != null) {
                    log.warn("[webhook] Used Stripe GSON fallback to deserialize Session from event: {} "
                            + "(API version mismatch handled)", stripeEvent.getId());
                    return session;
                }
            }
        } catch (Exception ex) {
            // Fix CC-1: pass ex itself (not ex.getMessage()) as the trailing arg so SLF4J
            // attaches the full stack trace instead of just the message text.
            log.error("[webhook] Stripe GSON fallback failed for event {}",
                    stripeEvent.getId(), ex);
        }

        log.error("[webhook] Could not deserialize Stripe Session from event: {}", stripeEvent.getId());
        return null;
    }
}
