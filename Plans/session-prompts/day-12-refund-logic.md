# Day 12 — Session Prompt

**Date:** Tuesday, April 15, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.txt` content, send this as your next message:

```text
We are on Day 12 — Pricing Engine Integration + Refund Logic + V11 Migration.
Feature: refund-logic

Active fixes today:
- Fix 12.1 — GOOD: refund_denial_reason field MUST be added via a new V11 migration (never edit V6)
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 11 complete: PricingEngine and WaitlistService tests passing ✅
- CANCELLED state and EVENT_CANCELLED transitions wired in state machine ✅
- Docker Desktop is RUNNING ✅

TDD MANDATORY — Tests FIRST (Red phase):
Write RefundServiceTest BEFORE any implementation:
  requestRefund_when7OrMoreDaysBeforeEvent_shouldIssueFullRefund()
  requestRefund_when3To6DaysBeforeEvent_shouldIssuePartialRefund()
  requestRefund_whenLessThan3DaysBeforeEvent_shouldDenyRefund()
  requestRefund_whenBookingNotConfirmed_shouldThrowConflictException()
  denyRefund_shouldPersistDenialReason()

Run ./mvnw test -Dtest=RefundServiceTest — ALL must FAIL before coding.

Non-negotiable rules:
- Use ChronoUnit.DAYS.between() for all day-distance calculations — never Duration.between().toDays()
- BusinessConstants.FULL_REFUND_DAYS_THRESHOLD = 7, PARTIAL_REFUND_DAYS_THRESHOLD = 3
- V11 is a NEW migration file. Do NOT edit V6 or any other existing migration.
- refund_denial_reason column is VARCHAR(255) NULLABLE on the refunds table.

Start with: Write RefundServiceTest with all 5 test method signatures. Confirm they fail.
```

---

## Context Briefing

**What we're building today:**
Day 12 integrates `PricingEngine` into `BookingService.reserveTickets()` and implements `RefundService` — the three-tier refund window logic and the denial reason field (Fix 12.1).

**Why refund timing correctness matters:**
The refund window is calculated relative to the event's start date, not the booking date. Using `Duration.between()` on two `Instant` values gives nanosecond precision and may round incorrectly at date boundaries. `ChronoUnit.DAYS.between()` is explicit and day-aligned — use it exclusively.

**Why Fix 12.1 is a new migration:**
Flyway migrations are immutable once run. Editing `V6__create_payments_and_refunds.sql` to add `refund_denial_reason` would corrupt the Flyway checksum and break all environments. The correct path is a new `V11__add_refund_denial_reason.sql`.

**Pre-conditions from Day 11:**

- PricingEngine: 3/3 tests passing ✅
- WaitlistService: 2/2 tests passing ✅
- CANCELLED + EVENT_CANCELLED state machine transitions wired ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 2, Day 12
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix 12.1** | 🟢 GOOD PRACTICE | Add `refund_denial_reason VARCHAR(255)` to the `refunds` table via a new **`V11__add_refund_denial_reason.sql`** migration. Do NOT edit any existing migration file. Update `Refund.java` entity to include the field. |

---

## Tasks (In Order)

### Morning (1 hr) — V11 Migration + Entity Update (Fix 12.1)

Create `src/main/resources/db/migration/V11__add_refund_denial_reason.sql`:

```sql
-- Fix 12.1: Add refund denial reason field
-- NEVER edit V6 — this is an additive migration only
ALTER TABLE refunds ADD COLUMN refund_denial_reason VARCHAR(255) NULL;
```

Update `Refund.java` entity:

```java
@Column(name = "refund_denial_reason")
private String refundDenialReason;
```

Verify Flyway applies cleanly:

```bash
./mvnw spring-boot:run
# Observe: "Successfully applied 1 migration to schema \"public\" (V11)"
```

### Afternoon (4 hrs) — Refund Logic + Pricing Integration

#### RefundService — Three-Tier Refund Window

```java
@Service @Transactional(readOnly = true)
@RequiredArgsConstructor
public class RefundService {

    private final BookingRepository bookingRepository;
    private final RefundRepository refundRepository;
    private final BookingStateMachineService stateMachineService;
    private final PaymentService paymentService;

    @Transactional
    public RefundResponse requestRefund(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getState().equals(BookingState.CONFIRMED)) {
            throw new ConflictException("Only CONFIRMED bookings can be refunded.");
        }

        long daysUntilEvent = ChronoUnit.DAYS.between(Instant.now(), booking.getEvent().getStartDate());

        Refund refund = Refund.builder()
            .bookingId(bookingId)
            .requestedAt(Instant.now())
            .build();

        if (daysUntilEvent >= BusinessConstants.FULL_REFUND_DAYS_THRESHOLD) {
            // Full refund (>= 7 days)
            refund.setAmount(booking.getTotalAmount());
            paymentService.refundAmount(booking.getStripeSessionId(), booking.getTotalAmount());
            stateMachineService.sendEvent(bookingId, BookingEvent.APPROVE_REFUND);
            refund.setStatus(RefundStatus.APPROVED);
        } else if (daysUntilEvent >= BusinessConstants.PARTIAL_REFUND_DAYS_THRESHOLD) {
            // Partial refund (3–6 days): 50% back
            BigDecimal partial = booking.getTotalAmount()
                .multiply(BigDecimal.valueOf(BusinessConstants.PARTIAL_REFUND_RATE));
            refund.setAmount(partial);
            paymentService.refundAmount(booking.getStripeSessionId(), partial);
            stateMachineService.sendEvent(bookingId, BookingEvent.APPROVE_REFUND);
            refund.setStatus(RefundStatus.APPROVED);
        } else {
            // No refund (< 3 days)
            refund.setAmount(BigDecimal.ZERO);
            refund.setRefundDenialReason("Event is within 3 days — no refund eligible.");
            stateMachineService.sendEvent(bookingId, BookingEvent.DENY_REFUND);
            refund.setStatus(RefundStatus.DENIED);
        }

        return RefundMapper.toResponse(refundRepository.save(refund));
    }
}
```

#### PricingEngine Integration into BookingService

In `BookingService.reserveTickets()`, after step 1 (validate event), calculate the final price:

```java
// After fetching the TicketTier:
BigDecimal finalPrice = pricingEngine.calculateFinalPrice(
    tier.getBasePrice(),
    event.getStartDate(),
    request.getQuantity(),
    inventoryService.getSoldCount(tier.getId()),
    tier.getTotalCapacity()
);
// Use finalPrice when creating the Booking and Ticket entities
```

#### New API Endpoint

```java
// BookingController (Class-level mapping: /api/v1/bookings)
@PostMapping("/{id}/refunds")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @AuthenticationPrincipal UserDetails userDetails) {
    // Note: The Idempotency-Key is mandated by core_api_protocols_and_contracts.md
    RefundResponse response = refundService.requestRefund(id, extractUserId(userDetails));
    return ResponseEntity.ok(ApiResponse.success(response));
}
```

### Evening (1 hr) — Green Phase Verification + Git

- Run `./mvnw test -Dtest=RefundServiceTest` — ALL 5 tests GREEN
- Run `./mvnw test` — entire test suite green
- Verify V11 migration applied: check `flyway_schema_history` table
- Git commit: `feat: implement refund service with 3-tier window logic and V11 migration`

---

## Expected Deliverable / Success Criteria

```text
[ ] V11__add_refund_denial_reason.sql created (NOT V6 edited) (Fix 12.1)
[ ] Refund.java entity has refundDenialReason field
[ ] Flyway applies V11 cleanly on startup
[ ] RefundServiceTest: 5/5 passing
[ ] Full refund path tested (>= 7 days) with mocked Stripe
[ ] Partial refund path tested (3–6 days) — 50% of total amount
[ ] Denial path tested (< 3 days) — reason stored in refund_denial_reason column
[ ] PricingEngine wired into BookingService.reserveTickets()
[ ] POST /api/v1/bookings/{id}/refunds endpoint live with Idempotency-Key header
[ ] ./mvnw test — entire test suite green
```

---

## Skills to Attach This Session

- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminders

1. **NEVER edit `V6__create_payments_and_refunds.sql`** — Flyway checksums are immutable (Fix 12.1)
2. Use `ChronoUnit.DAYS.between(Instant.now(), event.getStartDate())` — day-aligned calculation
3. The `< 3 days` branch must save `refundDenialReason` AND fire `DENY_REFUND` state machine event
4. `DENY_REFUND` triggers `DenyRefundNotificationAction` from Day 10 — the user gets an email automatically
