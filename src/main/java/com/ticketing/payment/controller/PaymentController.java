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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/bookings/{id}/checkout
     * Creates a Stripe Checkout Session for the given booking.
     * The booking must be in RESERVED state and owned by the authenticated user.
     *
     * @return CheckoutSessionResponse containing the Stripe-hosted checkout URL.
     */
    @Operation(summary = "Create a Stripe checkout session", description = "Creates or resumes a Stripe Checkout Session for a RESERVED/PAYMENT_PENDING booking owned by the caller.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Checkout session created — response contains the Stripe-hosted checkout URL")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller does not own this booking")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not in a checkout-eligible state")
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
