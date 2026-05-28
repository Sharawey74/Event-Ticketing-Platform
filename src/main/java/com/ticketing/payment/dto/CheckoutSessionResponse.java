package com.ticketing.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned to the frontend after creating a Stripe Checkout Session.
 * The frontend redirects the user to checkoutUrl immediately.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionResponse {

    /** The Stripe-hosted payment page URL — redirect the user here immediately. */
    private String checkoutUrl;

    /** The Stripe Checkout Session ID (cs_test_...) — stored on the Booking entity. */
    private String sessionId;

    /** The internal booking ID this checkout session is for. */
    private Long bookingId;
}
