package com.ticketing.payment.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stripe.exception.SignatureVerificationException;
import com.ticketing.common.security.JwtService;
import com.ticketing.payment.service.WebhookService;

/**
 * Slice test for StripeWebhookController — TDD Day 9.
 *
 * Uses @AutoConfigureMockMvc(addFilters = false) to bypass JwtFilter (consistent with
 * EventControllerTest, BookingControllerTest etc. across the codebase).
 * Security for the webhook endpoint is enforced via Stripe signature verification in WebhookService,
 * not via JWT — so disabling filters here is the correct test approach.
 *
 * Fix 9.1: Controller is NOT @Transactional — delegates to WebhookService.
 * Fix 9.1: HTTP 200 returned ONLY AFTER WebhookService.verifyAndProcess() completes (after DB commit).
 * 400 returned on bad signature — Stripe does NOT retry 400 responses.
 * 500 returned on unexpected failures — Stripe WILL retry on 5xx.
 */
@WebMvcTest(controllers = StripeWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "stripe.secret-key=sk_test_placeholder",
    "stripe.webhook-secret=whsec_placeholder"
})
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookService webhookService;

    // Required by @WebMvcTest security auto-configuration
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private static final String WEBHOOK_PAYLOAD =
            "{\"id\":\"evt_test_001\",\"type\":\"checkout.session.completed\"}";
    private static final String INVALID_SIG = "t=0000000000,v1=bad_signature";
    private static final String VALID_SIG   = "t=1234567890,v1=valid_signature_hash";

    @Test
    @DisplayName("handleWebhook: with invalid Stripe signature should return 400")
    void handleWebhook_withInvalidSignature_shouldReturn400() throws Exception {
        // Arrange: WebhookService throws SignatureVerificationException on bad sig
        doThrow(new SignatureVerificationException("Invalid signature", INVALID_SIG))
                .when(webhookService).verifyAndProcess(anyString(), anyString());

        // Act + Assert — 400: Stripe will NOT retry bad signature events
        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("Stripe-Signature", INVALID_SIG)
                        .content(WEBHOOK_PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("handleWebhook: with valid Stripe signature should return 200 after DB commit (Fix 9.1)")
    void handleWebhook_withValidSignature_shouldReturn200() throws Exception {
        // Arrange: WebhookService.verifyAndProcess() completes normally (void — no stubbing needed).
        // This simulates a successful transaction commit.

        // Act + Assert — 200: returned ONLY after verifyAndProcess() returns (Fix 9.1)
        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(WEBHOOK_PAYLOAD))
                .andExpect(status().isOk());
    }
}
