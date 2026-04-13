# Day 9 — Session Prompt
**Date:** Sunday, April 12, 2026 | **Planned Hours:** 7 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 9 — Stripe Checkout + Webhook.
Feature: stripe-webhook

Active fixes today:
- Fix 9.1 — CRITICAL: StripeWebhookController NOT @Transactional (return 200 AFTER commit)
- Fix 9.2 — CRITICAL: Catch DataIntegrityViolationException for true webhook idempotency
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 8 complete: BookingStateMachine tests 5/5 passing ✅
- reserveTickets() with TOCTOU double-check implemented ✅
- Stripe account + CLI installed ✅

TDD MANDATORY — Tests FIRST (Red phase):
Write PaymentServiceTest, StripeWebhookControllerTest, and WebhookServiceTest BEFORE any implementation:
  createCheckoutSession_forValidBooking_inReservedState_shouldReturnStripeUrl()
  createCheckoutSession_whenBookingNotInReservedState_shouldThrowConflictException()
  createCheckoutSession_whenBookingNotOwnedByUser_shouldThrowForbiddenException()
  handleWebhook_withInvalidSignature_shouldReturn400()
  handleWebhook_withValidSignature_shouldReturn200()
  processEvent_paymentSuccess_shouldTransitionBookingToConfirmed()
  processEvent_duplicateEvent_shouldThrowDataIntegrityViolation_andSilentlyIgnore()

Run ./mvnw test -Dtest=PaymentServiceTest,WebhookServiceTest — ALL must FAIL before coding.

Non-negotiable rules:
- Mock all Stripe SDK calls in tests (never call real Stripe API in unit tests).
- StripeWebhookController must be normal @RestController, delegating to a @Transactional service.
- DataIntegrityViolationException is the idempotency guard (requires unique constraint in DB schema).

Start with: Write PaymentServiceTest with all 3 test method signatures. Confirm they fail.
```

---

## Context Briefing

**What we're building today:**
Day 9 integrates Stripe — checkout session creation and webhook processing. This is the second-most-complex day. The Stripe webhook has two correctness requirements that the original plan gets wrong: the HTTP 200 timing and the idempotency guard. Both are fixed by the adjustments overlay.

**Why webhook correctness is existential:**
If the webhook returns 200 BEFORE the DB transaction commits and then the app crashes, the booking stays in `PAYMENT_PENDING` forever — a ghost reservation. If Stripe retries the webhook (which it will) and your idempotency guard is a `existsBy()` check with no unique constraint, two concurrent webhook deliveries will BOTH pass the check and BOTH commit a duplicate booking.

**Pre-conditions from Day 8:**
- BookingStateMachine tests: 5/5 passing ✅
- `reserveTickets()` with TOCTOU double-check implemented ✅
- Stripe account created + CLI installed (Fix PW3-1 from Day 1 or 2) ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 2, Day 9
- **Stripe implementation guide:** `Plans/Text/Phase1A_Sections 6,7,8,9_ImplementationGuides.txt`
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
|--------|----------|-----------------|
| **Fix 9.1** | 🔴 CRITICAL | `StripeWebhookController` must NOT be `@Transactional`. Return HTTP 200 only AFTER the DB transaction (called from a `@Transactional` service method) has committed. If the service throws, return 500 — this makes Stripe retry. |
| **Fix 9.2** | 🔴 CRITICAL | Idempotency guard: use `DataIntegrityViolationException` catch around the `processedEventRepository.save()`. The UNIQUE constraint on `stripe_event_id` is the hard guard. The `existsBy()` check alone is not safe under concurrent delivery. |

---

## Tasks (In Order)

### Morning (1.5 hrs) — Stripe Research + Write Tests First (Red — TDD)
- Read Stripe Checkout Java quickstart
- Read webhooks guide (signature verification section)
- Read test card numbers reference
- Verify Stripe account: `sk_test_*` and `pk_test_*` keys in `application-local.yml`
- Run: `stripe listen --forward-to localhost:8080/api/webhooks/stripe` — copy webhook secret

**After research, write ALL tests first before any implementation (TDD — Red phase):**
```java
// PaymentServiceTest.java — all Stripe SDK calls mocked with Mockito
createCheckoutSession_forValidBooking_inReservedState_shouldReturnStripeUrl()
createCheckoutSession_whenBookingNotInReservedState_shouldThrowConflictException()
createCheckoutSession_whenBookingNotOwnedByUser_shouldThrowForbiddenException()

// StripeWebhookControllerTest.java — @WebMvcTest
handleWebhook_withInvalidSignature_shouldReturn400()
handleWebhook_withValidSignature_shouldReturn200()

// WebhookServiceTest.java — unit test the service layer
processEvent_paymentSuccess_shouldTransitionBookingToConfirmed()
processEvent_duplicateEvent_shouldThrowDataIntegrityViolation_andSilentlyIgnore()
```
Run `./mvnw test -Dtest=PaymentServiceTest,WebhookServiceTest` — confirm ALL tests FAIL.
Do NOT write any production code until all tests are red.

### Afternoon (4.5 hrs) — Stripe Integration

#### StripeConfig
```java
@Configuration
public class StripeConfig {
    @Value("${stripe.secret-key}") private String secretKey;
    
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;  // Set the global Stripe API key
    }
}
```

Add to `application.yml`:
```yaml
stripe:
  secret-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  success-url: http://localhost:3000/bookings/{bookingId}/confirmation?session_id={CHECKOUT_SESSION_ID}
  cancel-url: http://localhost:3000/cart
```

#### PaymentService.createCheckoutSession()
7-step process:
1. Fetch booking — validate belongs to userId
2. Validate state = RESERVED (not any other state)
3. Transition to PAYMENT_PENDING via state machine (prevents second checkout attempt)
4. Build line items from `booking.getTickets()`
5. Create Stripe Session with payment mode, 30-min expiry, metadata: `{bookingId, userId}`
6. Save `Payment` entity: `stripe_session_id`, `status=PENDING`
7. Return `CheckoutSessionResponse` with the Stripe URL

#### StripeWebhookController (Fix 9.1 + Fix 9.2)
```java
@RestController  // NOT @Transactional — critical! (Fix 9.1)
public class StripeWebhookController {
    
    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        // 1. Verify signature — return 400 on failure
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        
        // 2. Delegate to @Transactional service (Fix 9.1 — 200 only after commit)
        try {
            webhookService.processEvent(event);  // @Transactional — DB commits here
            return ResponseEntity.ok().build();  // 200 ONLY after commit (Fix 9.1)
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();  // 500 → Stripe retries
        }
    }
}

// In WebhookService (@Transactional):
@Transactional
public void processEvent(Event event) {
    try {
        processedEventRepository.save(new ProcessedStripeEvent(event.getId()));
        // Unique constraint violation = already processed → log + return (Fix 9.2)
    } catch (DataIntegrityViolationException e) {
        log.info("Duplicate Stripe event ignored: {}", event.getId());
        return;
    }
    
    switch (event.getType()) {
        case "checkout.session.completed" -> handlePaymentSuccess(session);
        case "checkout.session.expired"   -> handlePaymentExpired(session);
    }
}
```

#### Local Webhook Testing
```bash
stripe listen --forward-to localhost:8080/api/webhooks/stripe
# Copy the webhook signing secret printed to console → set as STRIPE_WEBHOOK_SECRET
```

### Evening (1 hr) — Green Phase Verification + Git
- Run `./mvnw test -Dtest=PaymentServiceTest,WebhookServiceTest` — ALL 7 tests must be GREEN
- Run `./mvnw test` — entire test suite must pass
- Manual smoke test with Stripe CLI:
  ```bash
  stripe trigger checkout.session.completed  # fires test event
  # Verify: booking status → CONFIRMED in DB
  ```
- Git commit: `feat: implement stripe checkout and webhook handling`

---

## Expected Deliverable / Success Criteria

```
[ ] StripeWebhookController is NOT @Transactional (Fix 9.1)
[ ] 200 returned only AFTER service method commits (Fix 9.1)
[ ] DataIntegrityViolationException caught for duplicate events (Fix 9.2)
[ ] UNIQUE constraint on processed_stripe_events.stripe_event_id in V6 migration
[ ] Stripe checkout: POST /api/bookings/{id}/checkout → returns Stripe URL
[ ] Test payment with card 4242 4242 4242 4242 → webhook received
[ ] Booking transitions to CONFIRMED after successful payment
[ ] PaymentService unit tests: 3/3 passing (all Stripe calls mocked)
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminders
1. `StripeWebhookController` must NOT be `@Transactional` — the 200/500 response timing is intentional
2. The `existsBy()` check is NOT your idempotency guard — the UNIQUE constraint + `DataIntegrityViolationException` catch IS
3. Stripe retries on 500 — that's what we want when the DB fails
