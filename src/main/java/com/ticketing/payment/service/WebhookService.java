package com.ticketing.payment.service;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Optional<com.stripe.model.StripeObject> optional = deserializer.getObject();
        if (optional.isEmpty() || !(optional.get() instanceof Session session)) {
            log.error("[webhook] Could not deserialize Stripe Session from event: {}", stripeEvent.getId());
            return null;
        }
        return session;
    }
}
