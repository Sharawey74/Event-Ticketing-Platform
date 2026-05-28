package com.ticketing.payment.service;

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

        // Step 2: Validate booking is in RESERVED state
        if (booking.getState() != BookingState.RESERVED) {
            log.warn("[{}] [payment] Cannot create checkout — booking {} is in state {}, expected RESERVED",
                    correlationId, bookingId, booking.getState());
            throw new IllegalStateException(
                    "Checkout requires booking in RESERVED state, but was: " + booking.getState());
        }

        // Step 3: Build Stripe line items from booking tickets (group by tier)
        List<SessionCreateParams.LineItem> lineItems = booking.getTickets().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ticket -> ticket.getTier().getId(),
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> {
                    var tier = booking.getTickets().stream()
                            .filter(t -> t.getTier().getId().equals(entry.getKey()))
                            .findFirst()
                            .orElseThrow()
                            .getTier();
                    return SessionCreateParams.LineItem.builder()
                            .setQuantity(entry.getValue())
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(tier.getBasePrice()
                                            .multiply(java.math.BigDecimal.valueOf(100))
                                            .longValue()) // Stripe uses cents
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(tier.getTierName() + " — " + booking.getEvent().getTitle())
                                            .build())
                                    .build())
                            .build();
                })
                .toList();

        // Step 4: Build and create the Stripe Session
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl.replace("{bookingId}", String.valueOf(bookingId)))
                .setCancelUrl(cancelUrl)
                .setExpiresAt(Instant.now().plusSeconds(BusinessConstants.RESERVATION_TTL_SECONDS).getEpochSecond())
                .putMetadata("bookingId", String.valueOf(bookingId))
                .putMetadata("userId", String.valueOf(userId))
                .addAllLineItem(lineItems)
                .build();

        Session stripeSession;
        try {
            stripeSession = Session.create(params);
        } catch (StripeException e) {
            log.error("[{}] [payment] Stripe session creation failed for booking {}: {}",
                    correlationId, bookingId, e.getMessage());
            throw new RuntimeException("Failed to create Stripe checkout session", e);
        }

        // Step 5: Persist a Payment record with PENDING status
        Payment payment = Payment.builder()
                .booking(booking)
                .stripeSessionId(stripeSession.getId())
                .amount(booking.getTotalAmount())
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        // Step 6: Store session ID on the booking for webhook correlation
        booking.setStripeSessionId(stripeSession.getId());
        bookingRepository.save(booking);

        log.info("[{}] [payment] Checkout session created for booking {}. Session: {}",
                correlationId, bookingId, stripeSession.getId());

        // Step 7: Return response with Stripe URL
        return CheckoutSessionResponse.builder()
                .checkoutUrl(stripeSession.getUrl())
                .sessionId(stripeSession.getId())
                .bookingId(bookingId)
                .build();
    }
}
