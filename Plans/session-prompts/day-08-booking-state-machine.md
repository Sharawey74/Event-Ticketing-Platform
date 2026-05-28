# Day 8 — Session Prompt
**Date:** Saturday, April 11, 2026 | **Planned Hours:** 8 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 8 — Booking State Machine.
Feature: booking-state-machine

Active fixes today:
- Fix 8.1 — CRITICAL: reserveTickets() inside-lock double availability check
- Fix 8.2 — IMPORTANT: CHECK_IN has two guards (@PreAuthorize AND IsEventOrganizerGuard)
- Fix 8.3 — IMPORTANT: ReservationExpirationJob distributed lock
- Fix 11.1 — IMPORTANT: Add CANCELLED state and EVENT_CANCELLED event
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Week 1 complete: ./mvnw test all green ✅
- Docker Desktop is RUNNING ✅

TDD MANDATORY — Start with tests FIRST (Red phase):
Write BookingStateMachineTest BEFORE wiring up the BookingStateMachineConfig or BookingService.
  transition_fromAvailableToReserved_shouldLockSeatsInRedis()
  transition_fromReservedToExpired_afterFiveMinutes_shouldReleaseSeats()
  transition_fromPaymentPendingToConfirmed_shouldGenerateQRCode()
  transition_toConfirmed_shouldFireConfirmBookingAction()
  invalidTransition_fromConfirmedToReserved_shouldThrowException()

Run ./mvnw test -Dtest=BookingStateMachineTest — ALL 5 must FAIL before coding.

Non-negotiable rules:
- @EnableStateMachineFactory MUST be used — NEVER @EnableStateMachine
- Constructor injection via @RequiredArgsConstructor
- All timestamps: Instant (UTC)
- BookingState enum values from enums only — no raw strings
- Use BusinessConstants.RESERVATION_TTL_SECONDS and EXPIRY_JOB_INTERVAL_MS

Start by: Write BookingStateMachineTest with 5 tests, starting with bodies throwing NotImplemented. Confirm they all fail.
```

---

## Context Briefing

**What we're building today:**
Day 8 is the most complex day in the entire project. The Booking State Machine is the heart of the system — every ticket purchase, expiration, payment, refund, and check-in flows through it. Getting this right means getting the entire business logic right.

**Why this is the hardest day:**
1. Spring State Machine has a dangerous "shared singleton" antipattern — using `@EnableStateMachine` instead of `@EnableStateMachineFactory` creates ONE shared SSM across ALL concurrent requests. Under load, two concurrent bookings would interfere with each other's state.
2. The TOCTOU (Time of Check to Time of Use) race condition in `reserveTickets()` is the most subtle bug. Checking availability BEFORE acquiring the lock and AGAIN inside the lock are both required — the outside check is an optimization (fast fail), the inside check is the safety guard.
3. The `BookingState.CANCELLED` state is missing from the original plan (Fix 11.1) — add it now, not on Day 11.

**Pre-conditions from Day 7:**
- Week 1 checkpoint fully met ✅
- Docker Compose running 6+ services ✅
- Stripe account + CLI ready (Fix PW3-1) ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 2, Day 8
- **Implementation guides:** `Plans/Text/Phase1A_Sections 6,7,8,9_ImplementationGuides.txt`
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
|--------|----------|-----------------|
| **Fix 8.1** | 🔴 CRITICAL | In `reserveTickets()`, perform the availability check INSIDE the distributed lock body (not only before). The outside check is a fast-fail optimization; the inside check is the real guard against TOCTOU. |
| **Fix 8.2** | 🟡 IMPORTANT | The `CHECK_IN` transition needs TWO guards: `@PreAuthorize("hasRole('ORGANIZER')")` at HTTP layer + an `isEventOrganizer` guard at the state machine layer. Single-layer protection is not enough. |
| **Fix 8.3** | 🟡 IMPORTANT | `ReservationExpirationJob` (@Scheduled) must itself acquire a distributed lock before running, to prevent multiple app instances from processing the same expired bookings. TTL: 25s (< 30s interval). |
| **Fix 11.1** | 🟡 IMPORTANT | Add `CANCELLED` state and `EVENT_CANCELLED` event to the state machine NOW — affects 3 source states: CONFIRMED, RESERVED, PAYMENT_PENDING. Cheaper to add on Day 8 than refactor the SSM on Day 11. |

---

## Tasks (In Order)

### Morning (2 hrs) — State Machine Research + Diagram

- Read Baeldung Spring State Machine tutorial — focus on Events, Transitions, Guards, Actions
- Draw the complete Booking state machine diagram on paper:
  - **States:** AVAILABLE, RESERVED, PAYMENT_PENDING, CONFIRMED, ATTENDED, EXPIRED, RELEASED, PAYMENT_FAILED, REFUND_REQUESTED, REFUND_APPROVED, REFUND_DENIED, **CANCELLED** (Fix 11.1)
  - **Events:** ADD_TO_CART, PROCEED_TO_CHECKOUT, PAYMENT_SUCCESS, PAYMENT_FAILURE, TIMER_EXPIRED, CHECK_IN, REQUEST_REFUND, APPROVE_REFUND, DENY_REFUND, **EVENT_CANCELLED** (Fix 11.1)

### Afternoon (5 hrs)

#### Enums
```java
// BookingState: includes CANCELLED (Fix 11.1)
// BookingEvent: includes EVENT_CANCELLED (Fix 11.1)
// EventStatus: DRAFT, PUBLISHED, SALES_OPEN, SALES_CLOSED, COMPLETED, ARCHIVED
```

#### BookingStateMachineConfig
```java
@Configuration
@EnableStateMachineFactory  // CRITICAL: NEVER @EnableStateMachine — factory-per-request!
public class BookingStateMachineConfig 
    extends StateMachineConfigurerAdapter<BookingState, BookingEvent> {
    
    // Key transitions:
    // AVAILABLE → RESERVED on ADD_TO_CART (guard: seatsAvailableGuard, action: startReservationTimerAction)
    // RESERVED → PAYMENT_PENDING on PROCEED_TO_CHECKOUT
    // PAYMENT_PENDING → CONFIRMED on PAYMENT_SUCCESS (action: confirmBookingAction)
    // PAYMENT_PENDING → PAYMENT_FAILED on PAYMENT_FAILURE (action: releaseSeatsAction)
    // RESERVED → EXPIRED on TIMER_EXPIRED (action: releaseSeatsAction)
    // CONFIRMED → REFUND_REQUESTED on REQUEST_REFUND
    // REFUND_REQUESTED → REFUND_APPROVED on APPROVE_REFUND (action: releaseSeatsAction)
    // REFUND_REQUESTED → REFUND_DENIED on DENY_REFUND (action: denyRefundNotificationAction) [Fix 10.1]
    // CONFIRMED/RESERVED/PAYMENT_PENDING → CANCELLED on EVENT_CANCELLED [Fix 11.1]
    // CONFIRMED → ATTENDED on CHECK_IN (guard: isEventOrganizer) [Fix 8.2]
}
```

#### BookingStateMachineTest (TDD — Write FIRST)
```java
transition_fromAvailableToReserved_shouldLockSeatsInRedis()
transition_fromReservedToExpired_afterFiveMinutes_shouldReleaseSeats()
transition_fromPaymentPendingToConfirmed_shouldGenerateQRCode()
transition_toConfirmed_shouldFireConfirmBookingAction()
invalidTransition_fromConfirmedToReserved_shouldThrowException()
```

#### BookingService.reserveTickets() — 10-Step Process (Fix 8.1)
```java
@Transactional
public BookingResponse reserveTickets(ReserveTicketsRequest req, Long userId) {
    // Step 1: Validate event exists and status=SALES_OPEN
    // Step 2: Fast-fail check — getAvailableCount() (optimization, NOT the safety guard)
    // Step 3: Acquire Redis lock: SET tier:{tierId}:user:{userId} {uuid} NX EX 300
    //         Lock key is USER-SCOPED to prevent same user double-clicking
    try {
        // Step 4: DOUBLE-CHECK inside lock (FIX 8.1 — TOCTOU guard lives HERE)
        //         This re-check prevents two users racing on the last seat
        // Step 5: Decrement inventory via InventoryService.reserveSeat() (Lua script)
        // Step 6: Create Booking entity (state=RESERVED)
        // Step 7: Create Ticket entities (one per quantity)
        // Step 8: HSET cart:{userId} bookingId, tierId, quantity, eventId
        // Step 9: Set expires_at = Instant.now().plusSeconds(BusinessConstants.RESERVATION_TTL_SECONDS)
        // Step 10: Return BookingResponse
    } finally {
        // ALWAYS release lock — even on exception
        distributedLockService.releaseLock(lockKey, lockValue);
    }
}
```

#### ReservationExpirationJob (Fix 8.3)
```java
@Scheduled(fixedDelay = BusinessConstants.EXPIRY_JOB_INTERVAL_MS)
public void expireReservations() {
    // Acquire distributed lock FIRST (TTL: 25s < 30s interval)
    // Query: bookings WHERE state=RESERVED AND expires_at < now()
    // For each: send TIMER_EXPIRED to state machine → triggers releaseSeatsAction
    // Release lock in finally
}
```

### Evening (1 hr)
- Git commit: `feat: implement booking state machine with all transitions and guards`

---

## Expected Deliverable / Success Criteria

```
[ ] @EnableStateMachineFactory used — NEVER @EnableStateMachine
[ ] CANCELLED state and EVENT_CANCELLED event defined (Fix 11.1)
[ ] BookingStateMachine tests: 5/5 passing
[ ] reserveTickets() has double-check INSIDE the lock body (Fix 8.1)
[ ] CHECK_IN has both HTTP @PreAuthorize AND state machine guard (Fix 8.2)
[ ] ReservationExpirationJob acquires distributed lock before running (Fix 8.3)
[ ] Lock key uses user-scoped pattern: tier:{tierId}:user:{userId}
[ ] All BusinessConstants used (no magic numbers 300, 30000, etc.)
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminders
1. Use `@EnableStateMachineFactory` — NOT `@EnableStateMachine`
2. The inside-lock re-check (Fix 8.1) is non-negotiable — it's the TOCTOU guard
3. Lock key = user-scoped: `tier:{tierId}:user:{userId}` (not just `tier:{tierId}`)
4. Use `BusinessConstants.RESERVATION_TTL_SECONDS` — no magic number `300`
