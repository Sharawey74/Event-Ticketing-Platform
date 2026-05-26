# Day 16 — Session Prompt
**Date:** Saturday, April 19, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 16 — Test Coverage Push (80%+).
Feature: test-coverage

Active fixes today:
- Fix 16.1 — CRITICAL: 80% test coverage gate before deploy. Lua floor guard must be tested for concurrency.
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Week 2 features complete ✅
- Docker Desktop is RUNNING ✅

TDD MANDATORY — This day is all about tests:
Write ConcurrentBookingTest BEFORE any other coverage:
  testLuaFloorGuard_underHighConcurrency_shouldNeverOversell()

Run ./mvnw test -Dtest=ConcurrentBookingTest — confirm behavior.

Non-negotiable rules:
- Configure JaCoCo maven plugin to enforce an 80% minimum instruction coverage rule.
- PricingEngine must reach 100% branch coverage.
- ConcurrentBookingTest must spawn at least 100 threads attempting to book the same limited tier.

Start with: Configure JaCoCo in pom.xml and write ConcurrentBookingTest.
```

---

## Context Briefing

**What we're building today:**
Day 16 enforces the quality standard before production. We must configure JaCoCo to fail the build if instruction coverage drops below 80%. We must also write the most important test of the system: a multithreaded concurrency test proving the Redis Lua floor guard (Fix 5.1) prevents overselling.

**Why the Lua test (Fix 16.1) matters:**
The core value proposition of a ticketing system is "no double bookings". The Lua script in `InventoryService` ensures the atomic decrement doesn't drop below zero. We must prove this by firing 100 threads at a ticket tier with a capacity of 10. Exactly 10 should succeed, and exactly 90 should fail.

**Pre-conditions from Week 2:**
- BookingStateMachine tests passing ✅
- Payment + Refund tests passing ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 16
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix 16.1** | 🔴 CRITICAL | 80% test coverage gate. Configure JaCoCo to fail the build `< 80%`. Write a high-concurrency test (`ConcurrentBookingTest`) using an `ExecutorService` and `CountDownLatch` to blast the `InventoryService.reserveSeat()` method and prove the Lua floor guard works. |

---

## Tasks (In Order)

### Morning (1.5 hrs) — JaCoCo & Concurrency Test (Fix 16.1)

#### 1. JaCoCo Configuration (`pom.xml`)
Add the `jacoco-maven-plugin` to `<build><plugins>`.
Configure an `execution` for `check` phase with `<minimum>0.80</minimum>`.
Exclude DTOs, Entities, Exceptions, and Config classes from coverage.

#### 2. ConcurrentBookingTest
```java
@SpringBootTest
public class ConcurrentBookingTest {
    @Autowired private InventoryService inventoryService;
    // Test: 100 threads attempt to reserve 1 seat from a tier with capacity 10
    // Use ExecutorService and CountDownLatch
    // Assert: exactly 10 successes, 90 failures
    // Assert: inventoryService.getAvailableCount() == 0
}
```

### Afternoon (3.5 hrs) — Coverage Backfilling

Run `./mvnw clean test jacoco:report` and open `target/site/jacoco/index.html`.

Focus areas to backfill to reach 80%:
1. **PricingEngine** — 100% branch coverage required. Test combinations of early bird + group discounts.
2. **BookingController / EventController** — Write `@WebMvcTest` slices if controller coverage is low.
3. **WaitlistService** — Ensure edge cases (joining full tier, notifying) are covered.
4. **Exception Handlers** — Trigger 400, 404, 409 responses and assert body format.

### Evening (1 hr) — Verification + Git

- Run `./mvnw verify` — JaCoCo check must pass (coverage >= 80%).
- Git commit: `test: add JaCoCo 80% gate, concurrent booking test, and backfill coverage`

---

## Expected Deliverable / Success Criteria

```
[ ] JaCoCo plugin configured in pom.xml with 80% rule (Fix 16.1)
[ ] Exclusions added for DTOs and Entities
[ ] ConcurrentBookingTest written and passing (100 threads, no oversell) (Fix 16.1)
[ ] PricingEngine at 100% branch coverage
[ ] ./mvnw verify completes successfully (build does not fail on coverage)
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminders
1. Concurrent testing requires `CountDownLatch` to ensure all threads fire at exactly the same time.
2. JaCoCo checks instruction coverage, not just line coverage. 80% instruction coverage is the goal.
