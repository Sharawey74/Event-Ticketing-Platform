package com.ticketing.payment.controller;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.ticketing.payment.service.WebhookService;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles incoming Stripe webhook HTTP requests.
 *
 * Fix 9.1 — CRITICAL: This controller is NOT @Transactional.
 * The @Transactional boundary lives in WebhookService.verifyAndProcess().
 * HTTP 200 is returned ONLY after that method returns — meaning only after
 * the DB transaction has committed. If the commit fails, this method catches
 * the exception and returns 500 so Stripe retries delivery.
 *
 * Stripe retry behaviour:
 *   - 200 → Event delivered successfully. Stripe stops retrying.
 *   - 400 → Bad signature / malformed request. Stripe does NOT retry (our fault).
 *   - 500 → Internal error. Stripe WILL retry (up to 3 days, exponential backoff).
 *
 * NOTE: Spring Security is deliberately bypassed for this endpoint
 * because Stripe has no way to send a JWT. Security is enforced via
 * signature verification inside WebhookService.verifyAndProcess().
 */
@RestController  // NOT @Transactional — Fix 9.1
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class StripeWebhookController {

    private final WebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [webhook] Stripe webhook received.", correlationId);

        try {
            // Fix 9.1: verifyAndProcess() is @Transactional — DB commits inside there.
            // HTTP 200 is sent AFTER this call returns (i.e., AFTER commit).
            webhookService.verifyAndProcess(payload, sigHeader);
            return ResponseEntity.ok().build(); // 200 — ONLY sent after DB commit

        } catch (SignatureVerificationException e) {
            log.warn("[{}] [webhook] Invalid Stripe signature. Returning 400.", correlationId);
            return ResponseEntity.badRequest().build(); // 400 — Stripe won't retry

        } catch (Exception e) {
            log.error("[{}] [webhook] Webhook processing failed: {}", correlationId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build(); // 500 — Stripe WILL retry
        }
    }
}
