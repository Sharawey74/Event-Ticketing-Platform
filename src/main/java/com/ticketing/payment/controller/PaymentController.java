package com.ticketing.payment.controller;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.user.service.CustomUserDetails;
import com.ticketing.payment.dto.CheckoutSessionResponse;
import com.ticketing.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for payment operations.
 * Thin controller — all business logic lives in PaymentService.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/bookings/{id}/checkout
     * Creates a Stripe Checkout Session for the given booking.
     * The booking must be in RESERVED state and owned by the authenticated user.
     *
     * @return CheckoutSessionResponse containing the Stripe-hosted checkout URL.
     */
    @PostMapping("/{id}/checkout")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createCheckoutSession(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [payment] Checkout requested for booking {} by user {}",
                correlationId, id, userDetails.getId());

        CheckoutSessionResponse response = paymentService.createCheckoutSession(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
