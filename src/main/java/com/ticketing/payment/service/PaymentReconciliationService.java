package com.ticketing.payment.service;

import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Synchronous fallback for confirming a payment.
 *
 * A booking only leaves PAYMENT_PENDING when Stripe's asynchronous
 * {@code checkout.session.completed} webhook arrives. That is a single point of
 * failure: if no endpoint is registered, the host is unreachable, or the delivery
 * is simply lost, the card is charged and the booking still expires to
 * PAYMENT_FAILED via {@code ReservationExpirationJob}.
 *
 * Stripe returns {@code session_id} on the success redirect precisely so the
 * application can verify on return. This service performs that check and, when
 * Stripe reports the session paid, confirms through the same code the webhook
 * uses. The webhook remains the primary path — this only closes the gap when it
 * does not arrive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentReconciliationService {

    private final BookingRepository bookingRepository;
    private final WebhookService webhookService;

    /**
     * Seam over the Stripe static so this class stays unit-testable without
     * static mocking. Overridden in tests; never overridden in production.
     */
    protected Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    /**
     * Reconciles one booking against Stripe and returns its state afterwards.
     *
     * @param bookingId booking to check
     * @param userId    caller — must own the booking
     */
    @Transactional
    public BookingState reconcile(Long bookingId, Long userId) {
        String correlationId = MDC.get("correlationId");

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        // Object-level authorization: owning the booking is a separate question
        // from being allowed to call the endpoint.
        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Booking does not belong to the requesting user");
        }

        // Anything other than PAYMENT_PENDING is already settled — including a
        // booking the webhook confirmed a moment ago. Returning early keeps this
        // free of Stripe calls on the common path, where it runs on every visit
        // to the confirmation page.
        if (booking.getState() != BookingState.PAYMENT_PENDING) {
            return booking.getState();
        }

        String sessionId = booking.getStripeSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[{}] [reconcile] Booking {} is PAYMENT_PENDING with no Stripe session to check",
                    correlationId, bookingId);
            return booking.getState();
        }

        Session session;
        try {
            session = retrieveSession(sessionId);
        } catch (StripeException ex) {
            // A Stripe outage must not fail the confirmation page. The webhook may
            // still land, and the caller can retry.
            log.error("[{}] [reconcile] Could not retrieve Stripe session {} for booking {}",
                    correlationId, sessionId, bookingId, ex);
            return booking.getState();
        }

        if (session == null || !"paid".equals(session.getPaymentStatus())) {
            log.info("[{}] [reconcile] Booking {} not yet paid according to Stripe (status={})",
                    correlationId, bookingId,
                    session == null ? "no session" : session.getPaymentStatus());
            return booking.getState();
        }

        log.warn("[{}] [reconcile] Booking {} was paid but never confirmed by webhook — "
                + "confirming from the success redirect instead", correlationId, bookingId);

        webhookService.confirmPaidBooking(session, correlationId);

        return bookingRepository.findById(bookingId)
                .map(Booking::getState)
                .orElse(BookingState.PAYMENT_PENDING);
    }
}
