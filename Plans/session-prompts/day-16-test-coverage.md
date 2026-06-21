# Day 16 — Session Prompt

**Date:** Saturday, April 19, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 16 — Backend Test Coverage Push (80%+) + M-001 Retry Fix.
Feature: test-coverage

Active fixes today:
- Fix 16.1 — CRITICAL: 80% test coverage gate before deploy. Lua floor guard must be tested for concurrency.
- Fix M-001 — IMPORTANT: Add @Retryable to BookingService.checkIn() ONLY (NOT to reserveTickets).
- Cross-cutting: Fix CC-1 (X-Correlation-ID), Fix CC-2 (BusinessConstants only, no magic numbers)

Pre-conditions confirmed:
- Week 2 features complete (Days 8–15) ✅
- ./mvnw test → all tests passing ✅
- Docker Desktop is RUNNING ✅
- Frontend Days 13–15 complete ✅

TDD MANDATORY — This day is all about tests:
Write ConcurrentBookingTest BEFORE any other coverage:
  testLuaFloorGuard_underHighConcurrency_shouldNeverOversell()

Run ./mvnw test -Dtest=ConcurrentBookingTest — confirm behavior.

Non-negotiable rules:
- Configure JaCoCo maven plugin to enforce an 80% minimum instruction coverage rule.
- PricingEngine must reach 100% branch coverage.
- ConcurrentBookingTest must spawn at least 100 threads attempting to book the same limited tier.
- @Retryable must be applied ONLY to checkIn() — applying it to reserveTickets() causes permanent Redis inventory undercount.
- Every log.error() call must pass the exception as the second arg (not just the message).
- No hardcoded URLs, ports, or magic numbers — all via BusinessConstants.

Start with: Configure JaCoCo in pom.xml and write ConcurrentBookingTest.
```

---

## PRE-SESSION CHECKLIST (Do before opening VS Code)

```
[ ] Docker Desktop is OPEN and RUNNING
[ ] ./mvnw test passes cleanly with zero failures
[ ] JaCoCo is NOT yet configured in pom.xml (we add it today)
[ ] Read this full prompt before starting
```

---

## Context Briefing

**What we're building today:**
Day 16 enforces the quality standard before production. We configure JaCoCo to fail the build if instruction coverage drops below 80%. We write the most important test: a multithreaded concurrency test proving the Redis Lua floor guard (Fix 5.1) prevents overselling. We also apply M-001 (`@Retryable` on `checkIn()`) correctly.

**Why the Lua test (Fix 16.1) matters:**
The core value proposition of a ticketing system is "no double bookings". The Lua script in `InventoryService` ensures the atomic decrement doesn't drop below zero. We must prove this by firing 100 threads at a ticket tier with a capacity of 10. Exactly 10 should succeed, 90 should fail with `InsufficientInventoryException`.

**Why M-001 must be scoped to `checkIn()` ONLY:**
`reserveTickets()` decrements Redis inventory BEFORE the DB save. If `@Retryable` fires on an `ObjectOptimisticLockingFailureException` from the DB save, Redis would be decremented twice while only one booking is created — a permanent invisible inventory undercount. `checkIn()` only updates a DB row state (`CONFIRMED → ATTENDED`) with no Redis side effect — retrying it is safe.

**Pre-conditions from Day 15:**

- Frontend UI for all 10 pages complete ✅
- Stripe checkout + confirmation flow working ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 16
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix 16.1** | 🔴 CRITICAL | 80% test coverage gate. Configure JaCoCo to fail the build `< 80%`. Write a high-concurrency test (`ConcurrentBookingTest`) using an `ExecutorService` and `CountDownLatch` to blast `InventoryService.reserveSeat()` and prove the Lua floor guard works. |
| **Fix M-001** | 🟡 IMPORTANT | Add `@Retryable` for `ObjectOptimisticLockingFailureException` to `BookingService.checkIn()` ONLY. Enable `@EnableRetry` on main class. Do NOT add to `reserveTickets()`. |

---

## Tasks (In Order)

### Morning (1.5 hrs) — JaCoCo & Concurrency Test (Fix 16.1)

#### 1. JaCoCo Configuration (`pom.xml`)

Add the `jacoco-maven-plugin` to `<build><plugins>`:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.11</version>
  <executions>
    <execution>
      <id>prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>check</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>INSTRUCTION</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.80</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
        <excludes>
          <!-- Exclude DTOs, entities, config, exceptions — test coverage not meaningful -->
          <exclude>**/dto/**</exclude>
          <exclude>**/model/**</exclude>
          <exclude>**/config/**</exclude>
          <exclude>**/exception/**</exclude>
          <exclude>**/*Application.class</exclude>
          <exclude>**/util/BusinessConstants.class</exclude>
        </excludes>
      </configuration>
    </execution>
  </executions>
</plugin>
```

#### 2. ConcurrentBookingTest

```java
@SpringBootTest
@Transactional  // each test method gets a fresh DB state
public class ConcurrentBookingTest {

    @Autowired private InventoryService inventoryService;
    @Autowired private TicketTierRepository tierRepository;

    @Test
    @DisplayName("Fix 16.1: Lua floor guard must prevent overselling under 100 concurrent requests")
    void testLuaFloorGuard_underHighConcurrency_shouldNeverOversell() throws InterruptedException {
        int CAPACITY = 10;
        int THREADS = 100;
        // Set up a TicketTier in Redis with CAPACITY seats
        Long tierId = seedTierInRedis(CAPACITY);

        CountDownLatch startLatch = new CountDownLatch(1); // all threads start simultaneously
        CountDownLatch doneLatch = new CountDownLatch(THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for all threads to be ready
                    inventoryService.reserveSeat(tierId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown(); // fire all threads at once
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: exactly CAPACITY threads succeeded, the rest failed
        assertThat(successCount.get()).isEqualTo(CAPACITY);
        assertThat(failureCount.get()).isEqualTo(THREADS - CAPACITY);
        // Assert: Redis inventory is now exactly 0 (not negative)
        assertThat(inventoryService.getAvailableCount(tierId)).isZero();
    }
}
```

### Afternoon (3 hrs) — M-001 Retry Fix + Coverage Backfilling

#### Fix M-001 — @Retryable on checkIn() ONLY

Add `spring-retry` to `pom.xml` if not already present:

```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
</dependency>
```

Add `@EnableRetry` to `TicketingPlatformApplication.java`.

Apply `@Retryable` to `BookingService.checkIn()` ONLY:

```java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
@Transactional
public void checkIn(Long bookingId) {
    // ... existing checkIn logic — only DB state update, no Redis side effects
}
```

**⚠️ DO NOT add `@Retryable` to `reserveTickets()`** — the Redis decrement makes retrying unsafe.

#### Coverage Backfilling

Run `./mvnw clean test jacoco:report` and open `target/site/jacoco/index.html`.

Focus areas to backfill to reach 80%:

1. **PricingEngine** — 100% branch coverage required:

   ```java
   // Test all combinations:
   // - Event > 30 days away + group >= 5 → both discounts
   // - Event > 30 days away + group < 5 → only early bird
   // - Event <= 30 days + inventory > 80% → surge pricing
   // - Event <= 30 days + inventory <= 80% → base price
   ```

2. **BookingController / EventController** — `@WebMvcTest` slices:

   ```java
   @WebMvcTest(BookingController.class)
   class BookingControllerTest {
       @MockBean BookingService bookingService;
       @MockBean JwtService jwtService;
       // test 400, 422, 404, 409 responses
   }
   ```

3. **WaitlistService** — edge cases:
   - Joining a full tier → success (added to waitlist)
   - Notifying next-in-waitlist when cancellation happens
   - Attempting to join waitlist for an event that is not PUBLISHED

4. **GlobalExceptionHandler** — trigger all handlers:

   ```java
   // Trigger 400 (MethodArgumentNotValidException)
   // Trigger 404 (EntityNotFoundException)
   // Trigger 409 (ConstraintViolationException or duplicate)
   // Trigger 422 (IllegalStateException — e.g., event not open for booking)
   // Trigger 500 (catch-all Exception.class handler — e.g., mock RuntimeException)
   ```

5. **BookingStateMachineTest** — verify M-001 retry:
   - Mock `bookingRepository.save()` to throw `ObjectOptimisticLockingFailureException` on first call, succeed on second
   - Verify `checkIn()` completes successfully after one retry
   - Verify no double Redis decrement (mock inventoryService and assert single call)

### Evening (1 hr) — Vitest Frontend Setup + Verification + Git

#### Frontend Vitest Setup (deferred from Day 4 — apply today)

```bash
cd frontend
npm install -D vitest @vitejs/plugin-react @testing-library/react @testing-library/jest-dom
```

Add minimal `vitest.config.ts`:

```typescript
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
});
```

Write 2 smoke tests:

- `BookingStatusBadge.test.tsx` — renders correct text and bg class for CONFIRMED, EXPIRED, CANCELLED
- `authStore.test.ts` — confirms token is stored in sessionStorage, NOT localStorage

**Backend verification:**

- Run `./mvnw verify` — JaCoCo check must pass (coverage >= 80%)
- Run `./mvnw test -Dtest=ConcurrentBookingTest` — 10/100 successes confirmed
- Run `./mvnw test` — all tests passing with no failures

Git commit: `test: add JaCoCo 80% gate, concurrent booking test, M-001 retry, Vitest setup`

---

## Expected Deliverable / Success Criteria

```
[ ] JaCoCo plugin configured in pom.xml with 80% minimum instruction coverage rule
[ ] Exclusions added for DTOs, Entities, Config, Exceptions in JaCoCo config
[ ] ConcurrentBookingTest: 100 threads, 10 succeed, 90 fail, Redis count = 0 (Fix 16.1)
[ ] PricingEngine at 100% branch coverage (all 4 pricing scenarios tested)
[ ] WaitlistService: join-full and notify edge cases covered
[ ] GlobalExceptionHandler: all handlers triggered in tests (including 422 and catch-all 500)
[ ] @EnableRetry added to TicketingPlatformApplication.java (M-001)
[ ] @Retryable on BookingService.checkIn() only — NOT on reserveTickets() (M-001)
[ ] Retry test confirms checkIn() completes after one retry with single Redis call
[ ] ./mvnw verify completes successfully (build does not fail on coverage gate)
[ ] Vitest configured in frontend; BookingStatusBadge + authStore smoke tests passing
[ ] authStore test confirms sessionStorage used (NOT localStorage)
[ ] No magic numbers in any new test code — all via BusinessConstants
```

---

## Skills to Attach This Session

- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminders

1. **M-001 SCOPE**: `@Retryable` goes on `checkIn()` ONLY. Never on `reserveTickets()` — Redis makes it unsafe.
2. Concurrent testing requires `CountDownLatch` to ensure all threads fire at exactly the same time — `startLatch.await()` before the inventoryService call.
3. JaCoCo checks **instruction** coverage, not just line coverage. 80% instruction coverage is the goal.
4. The `log.error()` in GlobalExceptionHandler catch-all MUST pass `ex` as the second argument — `log.error("...", ex)` — without it, the stack trace never appears in server logs.
5. `./mvnw verify` (not just `./mvnw test`) runs the JaCoCo check phase. Use verify to confirm the gate.
6. **NEVER hardcode `localhost:8080`** — all config via environment variables and BusinessConstants only.

---

## 📋 Scope Analysis Reference

> **Full scope analysis (what is in/out of scope for Days 13–21):**
> `docs/Core/day13-21-scope-analysis.md`

### Priority Items Active This Day

| ID | Priority | Item | Status |
|----|----------|------|--------|
| BLOCKER-1 | 🔴 P1 | Verify `spring.profiles.active=local` is removed from `application.yaml` — if it is still present, remove it now before Day 18 | 🔲 Verify |
| HIGH-12 | 🟠 P2 | Run `git ls-files` hygiene check — confirm `Plans/`, `Archive/`, `.env`, `test_output.log`, `.vscode/` are NOT tracked in Git | 🔲 Verify |

### Items Confirmed Out of Scope for Day 16

| Item | Why |
|------|-----|
| Vitest test asserting `sessionStorage` usage | `authStore.ts` uses Zustand in-memory — not `sessionStorage`. A test for sessionStorage would be factually wrong |
| JaCoCo exclusions on service/controller classes | Exclusions are only acceptable for DTOs, entities, and config — not business logic |
| `@Retryable` on inventory/booking Redis operations | Explicitly forbidden: retrying Redis decrement paths causes invisible inventory undercount |
