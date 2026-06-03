package com.ticketing.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.common.exception.ConflictException;
import com.ticketing.common.util.BusinessConstants;
import com.ticketing.payment.dto.RefundResponse;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.Refund;
import com.ticketing.payment.model.RefundStatus;
import com.ticketing.payment.repository.PaymentRepository;
import com.ticketing.payment.repository.RefundRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the three-tier refund window policy.
 *
 * Refund tiers (relative to event start date):
 *   ≥ 7 days → FULL refund  (100% of totalAmount)
 *   3–6 days → PARTIAL refund (PARTIAL_REFUND_RATE = 50%)
 *   < 3 days → DENIED        (ZERO refund, reason stored on Booking — Fix 12.1)
 *
 * Key implementation rules:
 *   - ChronoUnit.DAYS.between() for all day calculations — NOT Duration.between().toDays()
 *     (DAYS.between is day-aligned; Duration.between gives nanosecond precision and rounds wrong)
 *   - All thresholds from BusinessConstants (Fix CC-2 — zero magic numbers)
 *   - Fix 12.1: refundDenialReason persisted on Booking entity (V11 migration already applied)
 *   - Fix CC-1: correlationId propagated in all log statements
 *   - Stripe call happens ONLY when money is returned (full/partial paths)
 *   - @Transactional at method level wraps the full refund + booking state update atomically
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RefundService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository  refundRepository;
    private final PaymentService    paymentService;

    /**
     * Processes a refund request for a confirmed booking.
     *
     * @param bookingId the booking to refund
     * @param userId    the authenticated user making the request (ownership check)
     * @return RefundResponse describing the outcome (approved with amount, or denied with reason)
     * @throws EntityNotFoundException if the booking or its payment record is not found
     * @throws AccessDeniedException   if the requesting user does not own the booking
     * @throws ConflictException       if the booking is not in CONFIRMED state
     */
    @Transactional
    public RefundResponse requestRefund(Long bookingId, Long userId) {
        String correlationId = MDC.get("correlationId");

        // ── Step 1: Fetch and validate booking ownership ──────────────────────
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            log.warn("[{}] [refund] Ownership violation — user {} attempted refund on booking {} owned by user {}",
                    correlationId, userId, bookingId, booking.getUser().getId());
            throw new AccessDeniedException("Booking does not belong to the requesting user");
        }

        // ── Step 2: State guard — only CONFIRMED bookings can request a refund ─
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ConflictException(
                    "Only CONFIRMED bookings can be refunded. Current state: " + booking.getState());
        }

        // ── Step 3: Fetch associated payment (needed for Stripe refund call) ──
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for booking: " + bookingId));

        // ── Step 4: Calculate days until event (Fix CC-2: ChronoUnit.DAYS) ────
        //    ChronoUnit.DAYS.between is day-aligned. Never use Duration.between().toDays()
        //    because it gives nanosecond precision and can round incorrectly at day boundaries.
        long daysUntilEvent = ChronoUnit.DAYS.between(Instant.now(), booking.getEvent().getStartDate());

        log.info("[{}] [refund] Refund requested for booking {} — {} days until event",
                correlationId, bookingId, daysUntilEvent);

        // ── Step 5: Apply the correct refund tier ─────────────────────────────
        Refund refund;
        if (daysUntilEvent >= BusinessConstants.FULL_REFUND_DAYS_THRESHOLD) {
            refund = processFullRefund(booking, payment, correlationId);
        } else if (daysUntilEvent >= BusinessConstants.PARTIAL_REFUND_DAYS_THRESHOLD) {
            refund = processPartialRefund(booking, payment, correlationId);
        } else {
            refund = processDeniedRefund(booking, payment, correlationId);
        }

        // ── Step 6: Persist refund record and updated booking state ───────────
        Refund savedRefund = refundRepository.save(refund);
        bookingRepository.save(booking); // persists state change + optional denial reason

        log.info("[{}] [refund] Refund {} ({}) saved for booking {}",
                correlationId, savedRefund.getStatus(), savedRefund.getAmount(), bookingId);

        return mapToResponse(savedRefund, booking);
    }

    // ── Private tier handlers ─────────────────────────────────────────────────

    /**
     * Full refund path: ≥ FULL_REFUND_DAYS_THRESHOLD (7) days before event.
     * Calls Stripe for the full booking amount. Transitions booking to REFUND_APPROVED.
     */
    private Refund processFullRefund(Booking booking, Payment payment, String correlationId) {
        BigDecimal amount = booking.getTotalAmount();
        paymentService.refundAmount(payment.getStripePaymentIntentId(), amount);
        booking.setState(BookingState.REFUND_APPROVED);
        log.info("[{}] [refund] Full refund of {} approved for booking {}",
                correlationId, amount, booking.getId());
        return buildRefund(payment, amount, RefundStatus.APPROVED, null);
    }

    /**
     * Partial refund path: PARTIAL_REFUND_DAYS_THRESHOLD (3) ≤ days < FULL_REFUND_DAYS_THRESHOLD (7).
     * Refunds PARTIAL_REFUND_RATE (50%) of the total amount via Stripe.
     * Transitions booking to REFUND_APPROVED.
     */
    private Refund processPartialRefund(Booking booking, Payment payment, String correlationId) {
        // Fix CC-2: PARTIAL_REFUND_RATE from BusinessConstants — never hardcode 0.50
        BigDecimal partial = booking.getTotalAmount()
                .multiply(BigDecimal.valueOf(BusinessConstants.PARTIAL_REFUND_RATE))
                .setScale(2, RoundingMode.HALF_UP);
        paymentService.refundAmount(payment.getStripePaymentIntentId(), partial);
        booking.setState(BookingState.REFUND_APPROVED);
        log.info("[{}] [refund] Partial refund of {} ({}%) approved for booking {}",
                correlationId, partial, (int)(BusinessConstants.PARTIAL_REFUND_RATE * 100), booking.getId());
        return buildRefund(payment, partial, RefundStatus.APPROVED, null);
    }

    /**
     * Denial path: < PARTIAL_REFUND_DAYS_THRESHOLD (3) days before event.
     * No Stripe call. Sets a human-readable denial reason on the Booking entity (Fix 12.1).
     * Transitions booking to REFUND_DENIED.
     */
    private Refund processDeniedRefund(Booking booking, Payment payment, String correlationId) {
        // Fix 12.1: Store denial reason on Booking so it appears on the user's dashboard
        String reason = String.format(
                "Refund not eligible: event starts in less than %d days. "
                + "Event date: %s. Refund request received: %s",
                BusinessConstants.PARTIAL_REFUND_DAYS_THRESHOLD,
                booking.getEvent().getStartDate(),
                Instant.now());
        booking.setRefundDenialReason(reason);
        booking.setState(BookingState.REFUND_DENIED);
        log.info("[{}] [refund] Refund denied for booking {} — within {}-day window",
                correlationId, booking.getId(), BusinessConstants.PARTIAL_REFUND_DAYS_THRESHOLD);
        // payment is always present for a CONFIRMED booking — required by payment_id NOT NULL FK
        return buildRefund(payment, BigDecimal.ZERO, RefundStatus.DENIED, reason);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Refund buildRefund(Payment payment, BigDecimal amount, RefundStatus status, String reason) {
        return Refund.builder()
                .payment(payment)
                .amount(amount)
                .status(status)
                .reason(reason)
                .build();
    }

    private RefundResponse mapToResponse(Refund refund, Booking booking) {
        return RefundResponse.builder()
                .refundId(refund.getId())
                .bookingId(booking.getId())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .refundDenialReason(booking.getRefundDenialReason())
                .requestedAt(refund.getRequestedAt())
                .processedAt(refund.getProcessedAt())
                .build();
    }
}
