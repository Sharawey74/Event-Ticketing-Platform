# Day 11 — Session Prompt
**Date:** Monday, April 14, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 11 — State Machine Full Config + Pricing Engine + Waitlist.
Feature: pricing-waitlist

Active fixes today:
- Fix 11.1 — IMPORTANT: CANCELLED state and EVENT_CANCELLED transitions (for CONFIRMED, RESERVED, PAYMENT_PENDING source states)
- Fix 11.2 — IMPORTANT: Confirm RELEASE event and AVAILABLE state are NOT present (already removed pre-Day 8)
- Fix 12.1 — GOOD: refund_denial_reason field requires new V11 migration (plan ahead)
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 10 complete: NotificationListenerIntegrationTest passing ✅
- QR codes generating async via ticket.generation.queue ✅
- Docker Desktop is RUNNING ✅

TDD MANDATORY — Tests FIRST (Red phase):
Write PricingServiceTest and WaitlistServiceTest BEFORE any implementation:
  applyPricing_whenEventIsMoreThan30DaysAway_shouldApply50PercentEarlyBirdDiscount()
  applyPricing_whenQuantityIs5OrMore_shouldApply10PercentGroupDiscount()
  applyPricing_whenInventoryAbove80Percent_shouldApply25PercentSurge()
  joinWaitlist_whenInventoryIsZero_shouldSaveWaitlistEntry()
  notifyWaitlist_whenSeatIsReleased_shouldPublishNotificationEvent()

Run ./mvnw test -Dtest=PricingServiceTest,WaitlistServiceTest — ALL must FAIL before coding.

Non-negotiable rules:
- ALL pricing thresholds and rates come from BusinessConstants — no magic numbers.
- ChronoUnit.DAYS.between() for all day-distance calculations.
- Pricing rules are additive — early bird + group discount can both apply.
- AVAILABLE and RELEASED are NOT valid BookingStates — do not use them (Fix 11.2).

Start with: Write PricingServiceTest with all 3 test method signatures. Confirm they fail.
```

---

## Context Briefing

**What we're building today:**
Day 11 completes the state machine configuration with the CANCELLED state and EVENT_CANCELLED transitions (Fix 11.1), then implements the PricingEngine and WaitlistService. These are two medium-complexity features that can be TDD'd cleanly with pure unit tests and no infrastructure dependencies.

**Why PricingEngine is clean to test:**
All three pricing rules (Early Bird, Group, Dynamic Surge) are pure functions: price × discount. Zero DB calls. Zero external services. Each rule is independently unit-testable and the constants live in BusinessConstants. Branch coverage to 100% is achievable on Day 16.

**Why WaitlistService matters:**
When all seats are taken, users need to join a waitlist. When a seat is released (EXPIRED or REFUND_APPROVED transition), the top of the waitlist gets notified. This is a RabbitMQ publish — the listener from Day 10 handles delivery.

**Pre-conditions from Day 10:**
- NotificationListenerIntegrationTest: 3/3 passing ✅
- DenyRefundNotificationAction wired in state machine ✅
- Async QR generation via ticket.generation.queue ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 2, Day 11
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix 11.1** | 🟡 IMPORTANT | Add `CANCELLED` state and `EVENT_CANCELLED` event to `BookingStateMachineConfig`. Must handle 3 source states: `CONFIRMED` (full refund action), `RESERVED` (release seats action), `PAYMENT_PENDING` (cancel payment session action). |
| **Fix 11.2** | 🟡 IMPORTANT | Verify `BookingState.AVAILABLE` and `BookingEvent.RELEASE` are NOT present in their enums. These were removed pre-Day 8. Confirm and document in a comment at the top of both enum files. |

---

## Tasks (In Order)

### Morning (1 hr) — State Machine Completion (Fix 11.1)

Add `CANCELLED` to `BookingState` and `EVENT_CANCELLED` to `BookingEvent`:

```java
// In BookingStateMachineConfig — add these transitions:
// CONFIRMED → CANCELLED on EVENT_CANCELLED (action: cancelBookingAction — full Stripe refund + notify)
// RESERVED → CANCELLED on EVENT_CANCELLED (action: releaseSeatsAction — no payment to refund)
// PAYMENT_PENDING → CANCELLED on EVENT_CANCELLED (action: cancelPaymentSessionAction)

// CancelBookingAction.java:
@Component @RequiredArgsConstructor
public class CancelBookingAction implements Action<BookingState, BookingEvent> {
    private final PaymentService paymentService;
    private final BookingEventPublisher publisher;

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
        // 1. Call paymentService.refundFullAmount(bookingId)
        // 2. Publish EmailNotificationEvent → "Your booking was cancelled due to event cancellation."
    }
}
```

In `EventService.cancelEvent()`:
```java
// Send EVENT_CANCELLED to all non-terminal bookings for that event:
bookingRepository.findByEventIdAndStateNotIn(eventId, TERMINAL_STATES)
    .forEach(b -> bookingStateMachineService.sendEvent(b.getId(), BookingEvent.EVENT_CANCELLED));
```

### Afternoon (4 hrs) — Pricing Engine + Waitlist

#### PricingEngine (Pure Unit-Testable Service)

```java
@Service
@RequiredArgsConstructor
public class PricingEngine {

    // Rule 1: Early Bird — 50% off if event is ≥ 30 days away
    public BigDecimal applyEarlyBirdDiscount(BigDecimal basePrice, Instant eventDate) {
        long daysUntilEvent = ChronoUnit.DAYS.between(Instant.now(), eventDate);
        if (daysUntilEvent >= BusinessConstants.EARLY_BIRD_DAYS_THRESHOLD) {
            return basePrice.multiply(BigDecimal.valueOf(1 - BusinessConstants.EARLY_BIRD_DISCOUNT));
        }
        return basePrice;
    }

    // Rule 2: Group Discount — 10% off for 5+ tickets
    public BigDecimal applyGroupDiscount(BigDecimal price, int quantity) {
        if (quantity >= BusinessConstants.GROUP_DISCOUNT_MIN_QUANTITY) {
            return price.multiply(BigDecimal.valueOf(1 - BusinessConstants.GROUP_DISCOUNT_RATE));
        }
        return price;
    }

    // Rule 3: Dynamic Surge — 25% markup if > 80% capacity sold
    public BigDecimal applyDynamicPricing(BigDecimal price, int sold, int capacity) {
        double occupancy = (double) sold / capacity;
        if (occupancy >= BusinessConstants.DYNAMIC_PRICING_THRESHOLD) {
            return price.multiply(BigDecimal.valueOf(1 + BusinessConstants.DYNAMIC_PRICING_SURGE));
        }
        return price;
    }

    // Combined: apply all applicable rules in order
    public BigDecimal calculateFinalPrice(BigDecimal basePrice, Instant eventDate, int quantity, int sold, int capacity) {
        BigDecimal price = applyEarlyBirdDiscount(basePrice, eventDate);
        price = applyGroupDiscount(price, quantity);
        price = applyDynamicPricing(price, sold, capacity);
        return price.setScale(2, RoundingMode.HALF_UP);
    }
}
```

#### WaitlistService

```java
@Service @Transactional(readOnly = true)
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final InventoryService inventoryService;
    private final BookingEventPublisher publisher;

    @Transactional
    public void joinWaitlist(Long userId, Long tierId) {
        if (inventoryService.getAvailableCount(tierId) > 0) {
            throw new BusinessRuleException("Seats still available — join directly.");
        }
        WaitlistEntry entry = WaitlistEntry.builder()
            .userId(userId).tierId(tierId).createdAt(Instant.now()).build();
        waitlistRepository.save(entry);
    }

    @Transactional
    public void notifyWaitlist(Long tierId, int releasedSeats) {
        List<WaitlistEntry> entries = waitlistRepository
            .findTopByTierIdOrderByCreatedAtAsc(tierId, releasedSeats);
        entries.forEach(entry ->
            publisher.publishEmailNotification(new EmailNotificationEvent(
                entry.getUserId(), "A seat is now available for your waitlisted event!", ...
            ))
        );
    }
}
```

Wire `notifyWaitlist()` into `releaseSeatsAction` — after releasing inventory, call `waitlistService.notifyWaitlist(tierId, quantity)`.

#### WaitlistEntry Entity + V10b Migration (if not yet migrated, use next available version)

```sql
CREATE TABLE waitlist_entries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    tier_id     BIGINT NOT NULL REFERENCES ticket_tiers(id),
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, tier_id)  -- one entry per user per tier
);
```

### Evening (1 hr) — Green Phase Verification + Git

- Run `./mvnw test -Dtest=PricingServiceTest,WaitlistServiceTest` — ALL 5 tests GREEN
- Run `./mvnw test` — all 68+ tests green
- Git commit: `feat: add pricing engine, waitlist service, and event cancellation state machine`

---

## Expected Deliverable / Success Criteria

```
[ ] CANCELLED state and EVENT_CANCELLED event defined and wired in state machine (Fix 11.1)
[ ] EVENT_CANCELLED handled for 3 source states: CONFIRMED, RESERVED, PAYMENT_PENDING
[ ] AVAILABLE and RELEASED NOT in BookingState enum (Fix 11.2 — verify)
[ ] RELEASE NOT in BookingEvent enum (Fix 11.2 — verify)
[ ] PricingServiceTest: 3/3 passing (all BusinessConstants used — zero magic numbers)
[ ] WaitlistServiceTest: 2/2 passing
[ ] waitlist_entries table created via migration
[ ] notifyWaitlist() called from releaseSeatsAction
[ ] ./mvnw test — entire test suite green
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminders
1. Pricing rules are additive — early bird AND group discount can both apply to the same booking
2. Use `ChronoUnit.DAYS.between()` — never `Duration.between().toDays()` (different semantics for dates)
3. Use `BusinessConstants` for EVERY threshold and rate — zero magic numbers (Fix CC-2)
4. `AVAILABLE` is an inventory state, not a booking state — do NOT add it back to `BookingState`
5. `releaseSeatsAction` must call `waitlistService.notifyWaitlist()` — otherwise waitlist never fires
