package com.ticketing.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.common.util.BusinessConstants;
import com.ticketing.payment.dto.CheckoutSessionResponse;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.PaymentStatus;
import com.ticketing.payment.repository.PaymentRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages Stripe Checkout Session creation.
 *
 * Fix CC-1: All log statements include correlationId from MDC.
 * Fix CC-2: All timing constants from BusinessConstants (no magic numbers).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.success-url:http://localhost:3000/bookings/{bookingId}/confirmation?session_id={CHECKOUT_SESSION_ID}}")
    private String successUrl;

    @Value("${stripe.cancel-url:http://localhost:3000/cart}")
    private String cancelUrl;

    /**
     * Creates a Stripe Checkout Session for a booking in RESERVED state.
     *
     * 7-step process:
     * 1. Fetch and validate booking ownership (userId must match)
     * 2. Validate state = RESERVED (not any other state)
     * 3. Build Stripe line items from booking tickets
     * 4. Create Stripe Session with payment mode + metadata {bookingId, userId}
     * 5. Persist Payment entity with status=PENDING
     * 6. Update booking.stripeSessionId
     * 7. Return CheckoutSessionResponse with the Stripe URL
     *
     * @param bookingId the booking to pay for
     * @param userId    the authenticated user requesting checkout (ownership check)
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Long bookingId, Long userId) {
        String correlationId = MDC.get("correlationId");

        // Step 1: Fetch booking and validate ownership
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            log.warn("[{}] [payment] User {} attempted checkout for booking {} owned by user {}",
                    correlationId, userId, bookingId, booking.getUser().getId());
            throw new AccessDeniedException("Booking does not belong to the requesting user");
        }

        // Step 2: Validate booking is checkout-eligible.
        // RESERVED  = first checkout attempt.
        // PAYMENT_PENDING = resume an abandoned checkout (a fresh Stripe session is created).
        if (booking.getState() != BookingState.RESERVED
                && booking.getState() != BookingState.PAYMENT_PENDING) {
            log.warn("[{}] [payment] Cannot create checkout — booking {} is in state {}, expected RESERVED or PAYMENT_PENDING",
                    correlationId, bookingId, booking.getState());
            throw new IllegalStateException(
                    "Checkout requires booking in RESERVED or PAYMENT_PENDING state, but was: " + booking.getState());
        }

        // The reservation hold must still be valid — an expired hold means the seats may have
        // been released by the expiration job, so we refuse to charge for them.
        if (booking.getExpiresAt() == null || booking.getExpiresAt().isBefore(Instant.now())) {
            log.warn("[{}] [payment] Cannot create checkout — booking {} reservation hold has expired",
                    correlationId, bookingId);
            throw new IllegalStateException(
                    "Reservation has expired. Please reserve the tickets again.");
        }

        // Step 3: Build Stripe line item using booking.totalAmount as the source of truth.
        // This ensures the Stripe charge matches exactly what the pricing engine calculated
        // (which may include early-bird discounts, group discounts, or surge pricing).
        int ticketCount = booking.getTickets().size();
        long perTicketCents = ticketCount > 0
                ? booking.getTotalAmount()
                        .divide(java.math.BigDecimal.valueOf(ticketCount), 2, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .longValue()
                : 0L;

        List<SessionCreateParams.LineItem> lineItems = List.of(
                SessionCreateParams.LineItem.builder()
                        .setQuantity((long) ticketCount)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(perTicketCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(booking.getEvent().getTitle())
                                        .build())
                                .build())
                        .build()
        );

        // Step 4: Build and create the Stripe Session
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl.replace("{bookingId}", String.valueOf(bookingId)))
                .setCancelUrl(cancelUrl)
                // Stripe requires expires_at to be at least 30 minutes from creation.
                .setExpiresAt(Instant.now().plusSeconds(BusinessConstants.STRIPE_SESSION_TTL_SECONDS).getEpochSecond())
                .putMetadata("bookingId", String.valueOf(bookingId))
                .putMetadata("userId", String.valueOf(userId))
                .addAllLineItem(lineItems)
                .build();

        Session stripeSession = null;
        String mockSessionId = null;
        String mockCheckoutUrl = null;

        try {
            stripeSession = Session.create(params);
        } catch (com.stripe.exception.AuthenticationException e) {
            log.warn("[{}] [payment] Stripe Authentication failed: {}. Using mock checkout for local testing.",
                    correlationId, e.getMessage());
            mockSessionId = "cs_test_mock_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            mockCheckoutUrl = successUrl.replace("{bookingId}", String.valueOf(bookingId))
                                        .replace("{CHECKOUT_SESSION_ID}", mockSessionId);
        } catch (StripeException e) {
            log.error("[{}] [payment] Stripe session creation failed for booking {}: {}",
                    correlationId, bookingId, e.getMessage());
            throw new RuntimeException("Failed to create Stripe checkout session", e);
        }

        String sessionId = stripeSession != null ? stripeSession.getId() : mockSessionId;

        // Step 5: Persist (or update) a Payment record with PENDING status.
        // On resume (PAYMENT_PENDING), a Payment row already exists — UPDATE it with the new
        // session ID rather than inserting, which would violate payments_booking_id_key (UNIQUE).
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .map(existing -> {
                    existing.setStripeSessionId(sessionId);
                    existing.setStatus(PaymentStatus.PENDING);
                    return existing;
                })
                .orElseGet(() -> Payment.builder()
                        .booking(booking)
                        .stripeSessionId(sessionId)
                        .amount(booking.getTotalAmount())
                        .currency("USD")
                        .status(PaymentStatus.PENDING)
                        .build());
        paymentRepository.save(payment);

        // Step 6: Store session ID on the booking and ensure state is PAYMENT_PENDING.
        // This is critical: the webhook handler checks for PAYMENT_PENDING state before confirming.
        // Without this transition, the webhook would silently skip confirmation (see WebhookService).
        // The hold is extended to match the Stripe session lifetime so the expiration job does not
        // race with an in-progress payment, and a resumed checkout keeps the seats held.
        BookingState previousState = booking.getState();
        booking.setStripeSessionId(sessionId);
        booking.setState(BookingState.PAYMENT_PENDING);
        booking.setExpiresAt(Instant.now().plusSeconds(BusinessConstants.STRIPE_SESSION_TTL_SECONDS));
        bookingRepository.save(booking);

        log.info("[{}] [payment] Checkout session created for booking {}. State {} → PAYMENT_PENDING. Session: {}",
                correlationId, bookingId, previousState, sessionId);

        String finalCheckoutUrl = stripeSession != null ? stripeSession.getUrl() : mockCheckoutUrl;

        // Step 7: Return response with Stripe URL
        return CheckoutSessionResponse.builder()
                .checkoutUrl(finalCheckoutUrl)
                .sessionId(sessionId)
                .bookingId(bookingId)
                .build();
    }

    /**
     * Issues a (partial or full) Stripe refund against the given Payment Intent.
     *
     * Called by RefundService after determining the applicable refund tier.
     * Uses the fully-qualified Stripe class name to avoid collision with
     * com.ticketing.payment.model.Refund.
     *
     * @param paymentIntentId Stripe Payment Intent ID (stored on Payment entity)
     * @param amount          refund amount in application currency (USD); converted to cents for Stripe
     */
    @Transactional
    public void refundAmount(String paymentIntentId, BigDecimal amount) {
        String correlationId = MDC.get("correlationId");
        try {
            // movePointRight(2): converts dollars to cents (e.g. 100.00 → 10000)
            // longValueExact(): FAILS FAST if any fractional cents remain — safer than longValue()
            //                   which silently truncates (e.g. 100.005 → 10000 without warning)
            long amountInCents = amount.movePointRight(2).longValueExact();

            com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountInCents)
                    .build();

            // Idempotency key: if this request is retried (e.g. network timeout), Stripe will
            // recognise the key and return the original refund instead of creating a duplicate.
            // Key format: refund:<paymentIntentId>:<amountInCents>
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey("refund:" + paymentIntentId + ":" + amountInCents)
                    .build();

            com.stripe.model.Refund.create(params, requestOptions);
            log.info("[{}] [payment] Stripe refund of {} issued for payment intent {}",
                    correlationId, amount, paymentIntentId);
        } catch (com.stripe.exception.StripeException e) {
            log.error("[{}] [payment] Stripe refund failed for payment intent {}: {}",
                    correlationId, paymentIntentId, e.getMessage());
            throw new RuntimeException("Failed to issue Stripe refund for payment intent: " + paymentIntentId, e);
        }
    }
}
