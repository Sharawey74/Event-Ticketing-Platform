package com.ticketing.booking.controller;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.booking.service.BookingService;
import com.ticketing.common.dto.ApiResponse;
import com.ticketing.payment.dto.RefundResponse;
import com.ticketing.payment.service.RefundService;
import com.ticketing.user.service.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Booking operations.
 * Thin controller — all business logic lives in BookingService / RefundService.
 *
 * Endpoints:
 *   POST /api/v1/bookings/{bookingId}/check-in  — ORGANIZER/ADMIN only
 *   POST /api/v1/bookings/{id}/refunds          — Any authenticated user (ownership enforced in service)
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final RefundService  refundService;

    /**
     * Check-in a booking (scan QR at venue).
     * Fix 8.2: Two guards — @PreAuthorize for role (HTTP layer) + CheckInGuard in state machine (domain layer).
     */
    @PostMapping("/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<Void> checkIn(@PathVariable Long bookingId) {
        bookingService.checkIn(bookingId);
        return ResponseEntity.ok().build();
    }

    /**
     * Request a refund for a confirmed booking.
     *
     * Three-tier refund window:
     *   ≥ 7 days before event  → full refund
     *   3–6 days before event  → 50% partial refund
     *   < 3 days before event  → denied (reason stored on booking — Fix 12.1)
     *
     * The Idempotency-Key header is accepted per API protocol but idempotency
     * enforcement is deferred to Day 20 (rate-limiting + denylist pass).
     *
     * Ownership is validated inside RefundService (booking.user.id == authenticated user id).
     */
    @PostMapping("/{id}/refunds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Refund requested for booking {} by user {}",
                correlationId, id, userDetails.getId());

        RefundResponse response = refundService.requestRefund(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
