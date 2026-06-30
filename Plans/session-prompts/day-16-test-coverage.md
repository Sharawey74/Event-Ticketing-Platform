# Day 16 — Session Prompt

**Date:** Saturday, April 19, 2026 | **Planned Hours:** 6 hrs

> **Rev:** Updated per `docs/Core/20_session_prompt_review.md` — BUG-D16-1 through D16-5 fixed, Fix 16B-missing added.

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.md` content, send this as your next message:

```
We are on Day 16 — Backend Test Coverage Push (80%+) + M-001 Retry Fix + BookingControllerTest.
Feature: test-coverage

Active fixes today:
- Fix 16.1 — CRITICAL: 80% INSTRUCTION coverage gate (JaCoCo). Lua floor guard concurrency test.
- Fix 16B-missing — CRITICAL: BookingControllerTest — @WebMvcTest + @Import(TestSecurityConfig.class), 9 tests.
- Fix M-001 — IMPORTANT: Add @Retryable to BookingService.checkIn() ONLY (NOT to reserveTickets).
- Cross-cutting: Fix CC-1 (X-Correlation-ID), Fix CC-2 (BusinessConstants only, no magic numbers)

Pre-conditions confirmed:
- Week 2 features complete (Days 8–15) ✅
- ./mvnw test → all tests passing ✅
- Docker Desktop is RUNNING ✅
- Frontend Days 13–15 complete ✅

TDD MANDATORY — This day is all about tests:
Write InventoryServiceConcurrencyTest BEFORE any other coverage:
  reserveSeat_whenConcurrentRequests_shouldNeverGoBelowZero()

CRITICAL test rules (BUG-D16-1 fix):
- ConcurrentBookingTest MUST NOT have @Transactional on the class — concurrent threads do NOT
  share the test's transaction context, causing wrong assertions. Use @BeforeEach/@AfterEach instead.
- Use @MockitoBean (NOT deprecated @MockBean) for all @WebMvcTest controller tests.
- JaCoCo counter is INSTRUCTION (not LINE) — these are different metrics. Be precise.

Non-negotiable rules:
- Configure JaCoCo maven plugin to enforce 80% minimum INSTRUCTION coverage (not LINE coverage).
- PricingEngine must reach 100% branch coverage.
- ConcurrentBookingTest must spawn 100 threads against 50-seat tier (50 succeed, 50 fail).
- @Retryable must be applied ONLY to checkIn() — applying it to reserveTickets() causes permanent Redis inventory undercount.
- Every log.error() call must pass the exception as the second arg (not just the message).
- No hardcoded URLs, ports, or magic numbers — all via BusinessConstants.

Start with: Configure JaCoCo in pom.xml (INSTRUCTION counter, 80%) and write InventoryServiceConcurrencyTest.
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
Day 16 enforces the quality standard before production. We configure JaCoCo to fail the build if INSTRUCTION coverage drops below 80%. We write the concurrency test proving the Lua floor guard prevents overselling. We write the full BookingControllerTest (Fix 16B-missing). We apply M-001 (`@Retryable` on `checkIn()`).

**Why the Lua test (Fix 16.1) matters:**
The core guarantee of a ticketing platform is "no oversell." 100 threads, 50 seats — exactly 50 must succeed. The Lua script's floor guard is the only correctness guarantee.

**Why M-001 must be scoped to `checkIn()` ONLY:**
`reserveTickets()` decrements Redis inventory BEFORE the DB save. If `@Retryable` fires on `ObjectOptimisticLockingFailureException`, Redis is decremented twice for one booking — a permanent invisible inventory undercount. `checkIn()` only updates a DB row state — no Redis side effects — so retrying is safe.

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 16
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix 16.1** | 🔴 CRITICAL | 80% INSTRUCTION coverage gate. JaCoCo `<counter>INSTRUCTION</counter>`. Write `InventoryServiceConcurrencyTest`: 100 threads / 50 seats / `CountDownLatch`. |
| **Fix 16B-missing** | 🔴 CRITICAL | `BookingControllerTest` — `@WebMvcTest` + `@Import(TestSecurityConfig.class)` + `@MockitoBean`. 9 tests covering all booking endpoints and `@PreAuthorize` guards. |
| **Fix M-001** | 🟡 IMPORTANT | `@Retryable` on `BookingService.checkIn()` ONLY. Enable `@EnableRetry`. Do NOT add to `reserveTickets()`. |

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
                <counter>INSTRUCTION</counter>  <!-- INSTRUCTION not LINE — more precise -->
                <value>COVEREDRATIO</value>
                <minimum>0.80</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
        <excludes>
          <!-- Only exclude non-logic classes — NEVER exclude services or controllers -->
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

#### 2. InventoryServiceConcurrencyTest (Fix 16.1)

**⚠️ BUG-D16-1 fix: NO `@Transactional` on this class.** Concurrent threads spawned by `ExecutorService` do NOT inherit the test's Spring transaction context. Each thread runs in its own transaction. Using `@Transactional` on the class causes incorrect assertion results. Use `@BeforeEach` / `@AfterEach` for setup and cleanup instead.

```java
@SpringBootTest
@Testcontainers
class InventoryServiceConcurrencyTest {

    // ⚠️ @SpringBootTest loads the FULL Spring context — all three infrastructure containers are required.
    // Omitting postgres or rabbitmq causes context startup failure even though this test only tests Redis.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     redis::getFirstMappedPort);
        registry.add("spring.rabbitmq.host",       rabbitmq::getHost);
        registry.add("spring.rabbitmq.amqp-port",  rabbitmq::getAmqpPort);
    }

    @Autowired InventoryService inventoryService;
    @Autowired TicketTierRepository tierRepository;

    private Long tierId;

    @BeforeEach
    void setUp() {
        // Commit to DB — each test gets a fresh tier with 50 seats
        TicketTier tier = tierRepository.save(
            TicketTier.builder().name("General").capacity(50).availableCount(50).build()
        );
        tierId = tier.getId();
        inventoryService.warmUpTier(tierId, 50);  // seeds Redis key
    }

    @AfterEach
    void tearDown() {
        tierRepository.deleteAll();
        // Redis cleared by Testcontainers container isolation per test
    }

    @Test
    @DisplayName("Fix 16.1: Lua floor guard — 100 threads / 50 seats → exactly 50 succeed, 0 negative")
    void reserveSeat_whenConcurrentRequests_shouldNeverGoBelowZero() throws InterruptedException {
        int SEAT_COUNT = 50;
        int THREAD_COUNT = 100;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);   // fire all threads simultaneously
        CountDownLatch doneLatch  = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();  // wait for all threads to be ready
                    Long result = inventoryService.reserveSeat(tierId, 1);
                    if (result != null && result >= 0) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();  // -1 = floor guard, -2 = key missing
                    }
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // release all 100 threads simultaneously
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(SEAT_COUNT);              // exactly 50 succeed
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - SEAT_COUNT);  // exactly 50 fail
        assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(0L);      // not negative
        assertThat(inventoryService.getAvailableCount(tierId)).isGreaterThanOrEqualTo(0L); // floor guard held
    }
}
```

---

### Mid-Morning (1 hr) — BookingControllerTest (Fix 16B-missing)

This is a mandatory CRITICAL fix from CLAUDE.md. Without it, `BookingController` has zero test coverage and the JaCoCo gate fails.

**⚠️ BUG-D16-4 fix: use `@MockitoBean` not deprecated `@MockBean`.**

```java
@WebMvcTest(controllers = BookingController.class)
@Import(TestSecurityConfig.class)          // MANDATORY — loads security without full context
class BookingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean BookingService bookingService;   // @MockitoBean (Spring Boot 3.4+, not @MockBean)
    @MockitoBean JwtService jwtService;           // MANDATORY — JwtFilter requires this bean

    // ❌ DO NOT add: addFilters = false — disables @PreAuthorize entirely
    // ❌ DO NOT mock: UserDetailsService — TestSecurityConfig provides it

    // --- RESERVE ---
    @Test
    @WithMockUser(roles = "USER")
    void bookingController_whenReserveValid_shouldReturn201() throws Exception {
        ReserveTicketsRequest request = new ReserveTicketsRequest(42L, 2);
        BookingResponse response = BookingResponse.builder()
            .bookingId(101L).state(BookingState.RESERVED).build();
        when(bookingService.reserveTickets(any(), anyLong())).thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.state").value("RESERVED"));
    }

    @Test
    void bookingController_whenReserveUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tierId\":42,\"quantity\":1}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void bookingController_whenReserveInvalidBody_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tierId\":null,\"quantity\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    // --- GET BOOKING ---
    @Test
    @WithMockUser(roles = "USER", username = "owner@test.com")
    void bookingController_whenGetBookingOwner_shouldReturn200() throws Exception {
        when(bookingService.getBookingDetails(eq(101L), anyLong()))
            .thenReturn(BookingDetailsResponse.builder().bookingId(101L).build());

        mockMvc.perform(get("/api/v1/bookings/101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "USER", username = "other@test.com")
    void bookingController_whenGetBookingOtherUser_shouldReturn403() throws Exception {
        when(bookingService.getBookingDetails(eq(101L), anyLong()))
            .thenThrow(new AccessDeniedException("Not your booking"));

        mockMvc.perform(get("/api/v1/bookings/101"))
            .andExpect(status().isForbidden());
    }

    // --- CHECK-IN ---
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void bookingController_whenCheckInAsOrganizer_shouldReturn200() throws Exception {
        when(bookingService.checkIn(eq(101L), anyLong()))
            .thenReturn(BookingResponse.builder().bookingId(101L).state(BookingState.ATTENDED).build());

        mockMvc.perform(post("/api/v1/bookings/101/check-in"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("ATTENDED"));
    }

    @Test
    @WithMockUser(roles = "USER")  // USER role — @PreAuthorize("hasRole('ORGANIZER')") must block this
    void bookingController_whenCheckInAsUser_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/101/check-in"))
            .andExpect(status().isForbidden());
    }

    // --- CANCEL ---
    @Test
    @WithMockUser(roles = "USER")
    void bookingController_whenCancelAsOwner_shouldReturn200() throws Exception {
        when(bookingService.cancelBooking(eq(101L), anyLong()))
            .thenReturn(BookingResponse.builder().bookingId(101L).state(BookingState.CANCELLED).build());

        mockMvc.perform(post("/api/v1/bookings/101/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("CANCELLED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void bookingController_whenCancelAsNonOwner_shouldReturn403() throws Exception {
        when(bookingService.cancelBooking(eq(101L), anyLong()))
            .thenThrow(new AccessDeniedException("Not your booking"));

        mockMvc.perform(post("/api/v1/bookings/101/cancel"))
            .andExpect(status().isForbidden());
    }
}
```

---

### Afternoon (2 hrs) — M-001 Retry + Coverage Backfilling

#### Fix M-001 — @Retryable on checkIn() ONLY

Add `spring-retry` to `pom.xml`:

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
public BookingResponse checkIn(Long bookingId, Long organizerId) {
    // ... DB state update only — no Redis side effects — safe to retry
}
```

**⚠️ DO NOT add `@Retryable` to `reserveTickets()`** — Redis decrement makes retrying unsafe (Fix M-001 addendum in `Phase1A_Adjustments_and_Fixes.md`).

#### Coverage Backfilling

Run `./mvnw clean test jacoco:report` and open `target/site/jacoco/index.html`.

Focus areas:

1. **PricingEngine — 100% branch coverage:**
   - Early bird + group discount (both combined)
   - Early bird only (group < min)
   - Surge pricing only (inventory > 80% threshold)
   - Base price (no discount, no surge)

2. **WaitlistService edge cases:**
   - Join full tier → added to waitlist
   - Notify next-in-waitlist on cancellation
   - Join waitlist for unpublished event → error

3. **GlobalExceptionHandler — all handlers triggered (Fix E-008):**
   - 400 (`MethodArgumentNotValidException`)
   - 404 (`EntityNotFoundException`)
   - 409 conflict
   - 429 (rate limit — if M-002 already applied)
   - 500 catch-all (`Exception.class` — every catch-all MUST include `ex` in `log.error()`)

4. **BookingStateMachineTest — verify M-001 retry:**
   - Mock `bookingRepository.save()` to throw `ObjectOptimisticLockingFailureException` on first call
   - Verify `checkIn()` completes after one retry
   - Verify NO double Redis decrement (mock `inventoryService` and assert single call)

---

### Evening (1 hr) — Vitest Frontend Setup + Verification + Git

#### Frontend Vitest Setup

```bash
cd frontend
npm install -D vitest @vitejs/plugin-react @testing-library/react @testing-library/jest-dom
```

Add `vitest.config.ts`:

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

**`BookingStatusBadge.test.tsx`:**
```typescript
// Tests rendering for each booking state — simple unit test
it('renders CONFIRMED with correct class', () => {
    render(<BookingStatusBadge state="CONFIRMED" />)
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
})
```

**`authStore.test.ts` (BUG-D16-3 fix — test Zustand state, not browser storage):**

⚠️ **Resolved contradiction:** authStore uses Zustand with `persist` middleware and `sessionStorage` as the storage adapter (Fix M-008). The test should verify the Zustand state API works correctly, NOT directly assert sessionStorage content (which is an implementation detail).

```typescript
// authStore.test.ts
import { useAuthStore } from '../stores/authStore'

beforeEach(() => {
    useAuthStore.setState({ token: null, user: null })
    sessionStorage.clear()
})

it('setAuth stores token and user in Zustand state', () => {
    const { setAuth } = useAuthStore.getState()
    setAuth('test-jwt', { id: 1, email: 'a@b.com', role: 'USER' })
    expect(useAuthStore.getState().token).toBe('test-jwt')
    expect(useAuthStore.getState().user?.email).toBe('a@b.com')
})

it('clearAuth resets token and user', () => {
    useAuthStore.setState({ token: 'old-jwt', user: { id: 1, email: 'a@b.com', role: 'USER' } })
    useAuthStore.getState().clearAuth()
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
})
```

#### Backend Verification

```bash
./mvnw verify          # JaCoCo check must pass (INSTRUCTION >= 80%)
./mvnw test -Dtest=InventoryServiceConcurrencyTest  # 50/100 successes confirmed
./mvnw test -Dtest=BookingControllerTest  # all 9 tests pass
./mvnw test            # all tests passing with no failures
```

Git commit: `test: add JaCoCo 80% instruction gate, Lua concurrency test, BookingControllerTest 9 tests, M-001 retry`

---

## Expected Deliverable / Success Criteria

```
[ ] JaCoCo plugin configured — counter: INSTRUCTION, minimum: 0.80 (not LINE)
[ ] Exclusions: DTOs, Entities, Config, Exceptions only — NOT services or controllers
[ ] InventoryServiceConcurrencyTest: NO @Transactional on class (BUG-D16-1 fix)
[ ] InventoryServiceConcurrencyTest: 100 threads / 50 seats / exactly 50 succeed
[ ] InventoryServiceConcurrencyTest: Redis count = 0 after all threads complete (never negative)
[ ] BookingControllerTest: @WebMvcTest + @Import(TestSecurityConfig.class) + @MockitoBean (not @MockBean)
[ ] BookingControllerTest: all 9 test methods present and passing (Fix 16B-missing)
[ ] PricingEngine: 100% branch coverage (all 4 pricing scenarios)
[ ] WaitlistService: join-full and notify edge cases covered
[ ] GlobalExceptionHandler: all handlers triggered including 500 catch-all with ex arg
[ ] @EnableRetry added to TicketingPlatformApplication.java
[ ] @Retryable on BookingService.checkIn() ONLY — NOT on reserveTickets()
[ ] Retry test: checkIn() completes after one retry, single Redis call confirmed
[ ] ./mvnw verify completes — JaCoCo INSTRUCTION gate passes
[ ] Vitest configured; BookingStatusBadge + authStore smoke tests passing
[ ] authStore test uses @MockitoBean approach — no direct sessionStorage assertion
[ ] No magic numbers in any test code — all via BusinessConstants
```

---

## Skills to Use This Session

- Invoke `/java-springboot` skill — available as a slash command (already in `.claude/skills/`)

## ⚠️ Critical Reminders

1. **BUG-D16-1 — NO `@Transactional` on concurrency test.** Threads spawned by `ExecutorService` do NOT share the Spring test transaction. Remove it — use `@BeforeEach`/`@AfterEach` instead.
2. **JaCoCo counter is `INSTRUCTION` — not `LINE`.** These measure different things. `INSTRUCTION` is stricter and more accurate.
3. **`@MockitoBean` not `@MockBean`** — `@MockBean` is deprecated in Spring Boot 3.4+.
4. **M-001 SCOPE**: `@Retryable` goes on `checkIn()` ONLY. NEVER on `reserveTickets()` — Redis decrement makes retry unsafe.
5. **Fix 16B-missing is CRITICAL** — without `BookingControllerTest`, `BookingController` has zero coverage and the JaCoCo gate fails.
6. `./mvnw verify` (not just `./mvnw test`) runs the JaCoCo check phase.
7. Every `log.error()` that catches an exception MUST pass `ex` as the last argument.

---

## 📋 Scope Analysis Reference

> **Full scope analysis (what is in/out of scope for Days 13–21):**
> `docs/Core/day13-21-scope-analysis.md`

### Priority Items Active This Day

| ID | Priority | Item | Status |
|----|----------|------|--------|
| BLOCKER-1 | 🔴 P1 | Verify `spring.profiles.active=local` removed from `application.yaml` | 🔲 Verify |
| HIGH-12 | 🟠 P2 | `git ls-files` hygiene check — confirm `Plans/`, `.env`, `target/` NOT tracked | 🔲 Verify |

### Items Confirmed Out of Scope for Day 16

| Item | Why |
|------|-----|
| JaCoCo exclusions on service/controller classes | Only DTOs, entities, config — never business logic |
| `@Retryable` on inventory/booking Redis operations | Explicitly forbidden — causes invisible inventory undercount |
| sessionStorage direct assertion in Vitest | Test Zustand state API behavior, not browser storage implementation detail |
