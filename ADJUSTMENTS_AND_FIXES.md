# Phase 1A — Adjustments & Fixes Overlay
> **Purpose:** This document is a companion overlay to the original Phase 1A execution plan (Sections 2–16).  
> It does NOT replace the original plan. It supplements it.  
> An AI agent reads the original plan section/day first, then reads the corresponding entry here to apply the fix or addition before generating any code, test, or configuration.  
> Every entry is tagged with its severity, its original plan location, and its exact implementation instruction.

---

## How to Use This Document (Agent Instructions)

1. Identify the current **Day** or **Section** being worked on from the original plan.
2. Find the matching entry below using the `[DAY X]` or `[SECTION N]` header.
3. Read the **Why** block to understand the problem the fix solves.
4. Apply the **Exact Fix** before or during the implementation of that day's task.
5. Mark the entry `✅ Applied` in your working context once implemented.
6. If a fix spans multiple days, re-check it on each relevant day.

**Severity Legend:**
- 🔴 CRITICAL — Skipping this causes a correctness bug (oversell, data loss, broken flow)
- 🟡 IMPORTANT — Skipping this causes maintainability or subtle runtime issues
- 🟢 GOOD PRACTICE — Skipping this is safe for Phase 1A but adds real value

## RECENT DISCOVERIES & AUDIT UPDATES (Pre-Day 8)
*The following fixes were discovered during the pre-Day 8 deep audit and MUST be addressed before proceeding.*

### BUG-01 — `InventoryService.warmUpInventoryCache()` Is Hollow (HIGH RISK)
**Severity:** 🔴 CRITICAL  
**Affects:** `InventoryService.java`
**Why:** The warm-up is commented out as a placeholder. The `InventoryWarmupHealthIndicator` marks itself `UP` immediately without loading any data into Redis. On a fresh deploy, every `reserveSeat()` call returns `-2` (key missing), causing ALL reservations to fail.
**Exact Fix:** Inject `TicketTierRepository` into `InventoryService` and load all tier counts into Redis before marking the health indicator `UP`.

### BUG-02 — `BookingState` Missing `AVAILABLE` and `CANCELLED` (Day 8 Design Conflict)
**Severity:** 🔴 CRITICAL  
**Affects:** `BookingState.java`, Day 8 State Machine
**Why:** Conflicting requirements. Day 8 prompt uses `AVAILABLE → RESERVED`. Fix 11.2 says to remove `AVAILABLE`. `CANCELLED` is missing.
**Exact Fix:** Remove `AVAILABLE` and `RELEASED` from the `BookingState` enum (they are not states, but actions/inventory counts). Add `CANCELLED` (for Fix 11.1).

### BUG-03 — `BookingRepository` Missing Critical Query Methods (Day 8 Blocker)
**Severity:** 🔴 CRITICAL  
**Affects:** `BookingRepository.java`
**Why:** The Day 8 `ReservationExpirationJob` requires `findByStateAndExpiresAtBefore`. `BookingService` requires `findByIdWithLock`.
**Exact Fix:** Add these methods to `BookingRepository`.

### BUG-04 — `TicketTier.maxPerBooking` Uses a Magic Number (Fix CC-2 Violation)
**Severity:** 🟡 IMPORTANT  
**Affects:** `TicketTier.java`, `BusinessConstants.java`
**Why:** Hardcoded `10` for max tickets per booking violates the rule to use `BusinessConstants`.
**Exact Fix:** Add `MAX_TICKETS_PER_BOOKING = 10` to `BusinessConstants` and use it in `TicketTier.@PrePersist`.

### INC-01 to INC-07 — Documentation Inconsistencies
**Severity:** 🟡 IMPORTANT  
**Affects:** Planning Documents
**Why:** Conflicting naming (`CheckInGuard` vs `IsEventOrganizerGuard`), mismatched Next.js versions (14 vs 15), and `RELEASED` orphan states cause agent confusion.
**Exact Fix:** 
- Use `CheckInGuard`.
- Treat `Next.js 15` as the true version.
- Remove `RELEASED` state from prompts.

---

## DAY 1 — Project Initialization + Database Schema

### Fix 1.1 — Use `Instant` Instead of `LocalDateTime` for Time-Sensitive Columns
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Section 2, Day 1 → JPA Entities  
**Affects:** `Booking`, `Payment`, `Refund`, `Event` entities

**Why:**  
`LocalDateTime` has no timezone information. The `expires_at` column on `Booking` (the 5-minute reservation countdown) will give wrong expiry windows if the server JVM timezone differs from UTC, or when the app is deployed to Railway (which runs UTC). A booking created at 11:58 PM local time with a 5-minute window could be calculated as expiring at 12:03 AM — or 11:03 PM — depending on timezone assumptions. `Instant` is a UTC epoch reference with no ambiguity.

**Exact Fix:**  
In all JPA entities, replace every `LocalDateTime` field that represents a point in time with `Instant`:
```java
// WRONG — in Booking.java, Payment.java, Refund.java, Event.java:
private LocalDateTime expiresAt;
private LocalDateTime createdAt;
private LocalDateTime startDate;

// CORRECT:
private Instant expiresAt;
private Instant createdAt;
private Instant startDate;
```
In Flyway migrations, use `TIMESTAMPTZ` (timestamp with time zone) instead of `TIMESTAMP`:
```sql
-- WRONG:
expires_at TIMESTAMP NOT NULL,

-- CORRECT:
expires_at TIMESTAMPTZ NOT NULL,
```
In `application.yml`, add:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```
Keep `LocalDate` only for date-only fields (e.g., a birth date with no time component). Use `Instant` for everything that represents a moment in time.

---

### Fix 1.2 — Use PostgreSQL ENUM for Role Column
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 1 → Flyway V1__create_users_table.sql  
**Affects:** `users` table, `Role` enum, `User` entity

**Why:**  
The plan stores role as `VARCHAR(20)`. A typo like `"ORGANISER"` (British spelling) stores silently and causes a null or wrong role at runtime. The DB has no constraint enforcing valid values. A PostgreSQL ENUM type makes invalid role values a DB-level error, not a silent application bug.

**Exact Fix:**  
In `V1__create_users_table.sql`, before the `CREATE TABLE` statement:
```sql
CREATE TYPE user_role AS ENUM ('USER', 'ORGANIZER', 'ADMIN');
```
Then in the table definition:
```sql
role user_role NOT NULL DEFAULT 'USER',
```
In `User.java` entity, add the Hibernate annotation to map Java enum to PostgreSQL enum:
```java
@Enumerated(EnumType.STRING)
@Column(columnDefinition = "user_role")
private Role role;
```

---

### Fix 1.3 — Add Soft Delete Column to Bookings Table
**Severity:** 🟢 GOOD PRACTICE  
**Original Plan Location:** Section 2, Day 1 → V5__create_bookings_and_tickets.sql  
**Affects:** `bookings` table

**Why:**  
The plan's `DELETE /api/bookings/{id}` triggers a cancellation flow. Hard-deleting a booking violates audit requirements — you lose the payment record, the seat history, and the refund trail. Soft delete keeps the row and marks it invisible to normal queries.

**Exact Fix:**  
In `V5__create_bookings_and_tickets.sql`, add to the bookings table:
```sql
deleted_at TIMESTAMPTZ NULL DEFAULT NULL,
```
In `Booking.java`:
```java
@Column(name = "deleted_at")
private Instant deletedAt;

public boolean isDeleted() {
    return deletedAt != null;
}
```
In `BookingRepository.java`, add `@Where(clause = "deleted_at IS NULL")` on the entity or use explicit query filters. Cancellation sets `deletedAt = Instant.now()` instead of calling `delete()`.

---

## DAY 2 — Event Service + Auth Integration

### Fix 2.1 — Add `@Transactional(readOnly = true)` on All Read Methods
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 2 → EventService implementation  
**Affects:** Every `get*` and `find*` method in all service classes

**Why:**  
Without `readOnly = true`, Hibernate runs dirty checking on every entity loaded in a read operation — comparing every field to its loaded state before the transaction closes. This is wasted CPU on read-only paths. More importantly, `readOnly = true` is the signal to Spring that allows future connection routing to a read replica. It also makes intent explicit: a reviewer can immediately see which methods mutate state.

**Exact Fix:**  
Apply this pattern to every service method that only reads data:
```java
// On the class — sets the default for all methods:
@Service
@Transactional(readOnly = true)
public class EventService {

    // Write methods explicitly override to readOnly=false:
    @Transactional
    public EventResponse createEvent(CreateEventRequest req, Long organizerId) { ... }

    @Transactional
    public EventResponse updateEvent(Long id, UpdateEventRequest req, Long organizerId) { ... }

    // Read methods inherit readOnly=true from the class annotation — no additional annotation needed:
    public EventResponse getEventById(Long id) { ... }
    public Page<EventResponse> getEvents(EventFilterRequest filter, Pageable pageable) { ... }
}
```
Apply this pattern to: `EventService`, `BookingService`, `UserService`, `PaymentService`, `InventoryService`, `WaitlistService`.

---

### Fix 2.2 — Never Use `@Autowired` Field Injection — Constructor Injection Only
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 2 → All service classes  
**Affects:** Every `@Service`, `@Component`, `@RestController` class

**Why:**  
Field-injected dependencies cannot be set in unit tests without a Spring context. Constructor injection makes dependencies explicit, enables `@InjectMocks` in Mockito, and allows the compiler to detect missing dependencies. The plan mentions this in the Clean Code checklist — it must be enforced from Day 2 onward, not retrofitted on Day 20.

**Exact Fix:**  
Use `@RequiredArgsConstructor` from Lombok on every class — it generates a constructor for all `final` fields:
```java
// WRONG:
@Service
public class EventService {
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private InventoryService inventoryService;
}

// CORRECT:
@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final InventoryService inventoryService;
}
```
All fields must be `final`. No `@Autowired` annotations anywhere in the codebase.

---

## DAY 5 — InventoryService + Redis Cache + RabbitMQ Config

### Fix 5.1 — Use Lua Script Floor Guard in `reserveSeat()` (Not Plain DECR)
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Section 2, Day 5 → InventoryService  
**Affects:** `InventoryService.reserveSeat()`

**Why:**  
Redis `DECR` is atomic but unconditional. If `available_count` is 0 and two threads both call `DECR`, both succeed — the count becomes -1 and then -2. You've now sold tickets that don't exist. The plan states that `reserveSeat()` "returns false if the count would go below zero" — this behavior requires a Lua script with a conditional check. A plain `DECR` will not enforce this floor.

**Exact Fix:**  
In `InventoryService.java`, implement `reserveSeat()` using a Lua script instead of `redisTemplate.opsForValue().decrement()`:

```java
private static final String RESERVE_SEAT_LUA =
    "local count = redis.call('GET', KEYS[1]) " +
    "if count == false then return -2 end " +           -- key doesn't exist
    "if tonumber(count) >= tonumber(ARGV[1]) then " +
    "  return redis.call('DECRBY', KEYS[1], ARGV[1]) " + -- atomic decrement
    "else " +
    "  return -1 " +                                     -- insufficient stock
    "end";

public boolean reserveSeat(Long tierId, int quantity) {
    String key = "inventory:tier:" + tierId + ":available";
    DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESERVE_SEAT_LUA, Long.class);
    Long result = redisTemplate.execute(
        script,
        Collections.singletonList(key),
        String.valueOf(quantity)
    );
    if (result == null || result < 0) {
        return false; // -1 = insufficient stock, -2 = key missing
    }
    return true; // result = new count (>= 0)
}
```

Return values: `>= 0` means success (new count), `-1` means insufficient stock, `-2` means key not found (treat as insufficient). The caller (`BookingService`) throws `InsufficientInventoryException` on `false`.

---

### Fix 5.2 — Add Redis Startup Health Block Until Inventory Warm-Up Completes
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 5 → InventoryService `@PostConstruct`  
**Affects:** Application startup, `InventoryService`

**Why:**  
The plan warms up Redis inventory counts from the DB on `@PostConstruct`. But between the moment the Spring Boot app starts accepting HTTP requests and the moment `@PostConstruct` completes, incoming reservation requests read a Redis key that doesn't exist yet — `reserveSeat()` receives the `-2` (key missing) result and incorrectly rejects valid reservations. On Railway with rolling restarts, this window is real.

**Exact Fix:**  
Use a `ReadinessIndicator` to delay Railway's health check until warm-up is complete:
```java
@Component
public class InventoryWarmupHealthIndicator implements HealthIndicator {

    private volatile boolean warmupComplete = false;

    public void markWarmupComplete() {
        this.warmupComplete = true;
    }

    @Override
    public Health health() {
        return warmupComplete
            ? Health.up().build()
            : Health.down().withDetail("reason", "Inventory warm-up in progress").build();
    }
}
```
In `InventoryService.@PostConstruct`:
```java
@PostConstruct
public void warmUpInventoryCache() {
    // ... existing warm-up logic loading all tier counts from DB into Redis ...
    inventoryWarmupHealthIndicator.markWarmupComplete(); // signal readiness last
}
```
In `application.yml`:
```yaml
management:
  endpoint:
    health:
      show-details: always
  health:
    readinessstate:
      enabled: true
```

---

## DAY 8 — Booking State Machine + `reserveTickets()`

### Fix 8.1 — Double-Check Availability INSIDE the Lock (TOCTOU Prevention)
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Section 2, Day 8 → `BookingService.reserveTickets()`  
**Affects:** `BookingService.reserveTickets()`, `DistributedLockService.executeWithLock()`

**Why:**  
The lock key is scoped to `tier:{tierId}:user:{userId}` — intentionally, to prevent the same user from double-clicking. But two different users (user:99 and user:77) both get their own lock keys and can acquire them simultaneously. If both users read "1 seat available" before either acquires their lock, and then both acquire their respective user-scoped locks and decrement, the inventory goes to -1. The fix is a second availability check inside the lock body, after the lock is held.

**Exact Fix:**  
In `BookingService.reserveTickets()`, the `executeWithLock()` lambda must re-check availability:
```java
public BookingResponse reserveTickets(ReserveTicketsRequest request, Long userId) {
    String lockKey = "seat:lock:tier:" + request.getTierId() + ":user:" + userId;

    return distributedLockService.executeWithLock(lockKey, 300L, () -> {

        // ✅ DOUBLE-CHECK INSIDE THE LOCK — this is the TOCTOU fix:
        int available = inventoryService.getAvailableCount(request.getTierId());
        if (available < request.getQuantity()) {
            throw new InsufficientInventoryException(
                "Insufficient seats for tier: " + request.getTierId() +
                " — requested: " + request.getQuantity() +
                " — available: " + available
            );
        }

        // Now safely reserve — no other thread can be here for this tier+user combo,
        // AND we've confirmed stock is available as of lock acquisition time:
        boolean reserved = inventoryService.reserveSeat(request.getTierId(), request.getQuantity());
        if (!reserved) {
            throw new InsufficientInventoryException("Reservation failed — stock depleted");
        }

        // ... rest of booking creation logic
    });
}
```
The first availability check (before acquiring the lock) is the fast path for user-facing rejection. The second check (inside the lock) is the correctness guarantee.

---

### Fix 8.2 — Add `CHECK_IN` Guard (Organizer-Only + Event-Scoped)
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 8 → State Machine config, `BookingStateMachineConfig`  
**Affects:** `CHECK_IN` transition, `BookingController`

**Why:**  
The state machine transition `CONFIRMED → CHECK_IN → ATTENDED` has no guard. Any authenticated user — including the ticket holder themselves — can call the check-in API and mark their own ticket as attended, bypassing the organizer's gate. The guard must enforce two things: the caller has the ORGANIZER role, AND the organizer owns the event the booking belongs to.

**Exact Fix:**  
Create `CheckInGuard.java`:
```java
@Component
@RequiredArgsConstructor
public class CheckInGuard implements Guard<BookingState, BookingEvent> {

    private final BookingRepository bookingRepository;

    @Override
    public boolean evaluate(StateContext<BookingState, BookingEvent> context) {
        Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
        Long currentUserId = (Long) context.getExtendedState().getVariables().get("currentUserId");

        return bookingRepository.findById(bookingId)
            .map(booking -> booking.getEvent().getOrganizerId().equals(currentUserId))
            .orElse(false);
    }
}
```
Wire it in `BookingStateMachineConfig`:
```java
.withExternal()
    .source(BookingState.CONFIRMED)
    .target(BookingState.ATTENDED)
    .event(BookingEvent.CHECK_IN)
    .guard(checkInGuard)  // ← add this
```
In `BookingController`, annotate the check-in endpoint:
```java
@PostMapping("/{id}/check-in")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<ApiResponse<BookingResponse>> checkIn(@PathVariable Long id) { ... }
```
The `@PreAuthorize` handles the role check at the HTTP layer. The guard handles event-scoping at the state machine layer. Both are required.

---

### Fix 8.3 — Add `@Scheduled` Expiry Job Distributed Lock (Multi-Replica Safe)
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 8 → `ReservationExpirationJob`  
**Affects:** `ReservationExpirationJob.@Scheduled`

**Why:**  
If Railway scales the app to 2+ instances, every instance runs `@Scheduled` independently. Both instances could find the same expired booking, both call `sendEvent(TIMER_EXPIRED)`, and both attempt the state transition. The second attempt would get an `InvalidStateTransitionException` (transition from EXPIRED is not valid), which is handled — but it's a noisy error and wastes DB calls. Add a distributed lock around the job so only one instance runs it per 30-second window.

**Exact Fix:**  
In `ReservationExpirationJob.java`:
```java
@Scheduled(fixedDelay = 30_000)
public void expireStaleReservations() {
    String lockKey = "scheduler:reservation-expiry";
    String lockValue = UUID.randomUUID().toString();

    boolean acquired = distributedLockService.acquireLock(lockKey, lockValue, 25L); // 25s < 30s interval
    if (!acquired) {
        log.debug("Expiry job skipped — another instance is running it");
        return;
    }

    try {
        List<Booking> expired = bookingRepository
            .findByStateAndExpiresAtBefore(BookingState.RESERVED, Instant.now());
        for (Booking booking : expired) {
            bookingStateMachineService.sendEvent(booking.getId(), BookingEvent.TIMER_EXPIRED);
        }
    } finally {
        distributedLockService.releaseLock(lockKey, lockValue);
    }
}
```

---

## DAY 9 — PaymentService + Stripe Webhook

### Fix 9.1 — Return 200 Only After DB Commit (Webhook Reliability)
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Section 2, Day 9 → Stripe Webhook handler  
**Affects:** `WebhookController`, `PaymentService`

**Why:**  
Stripe considers a webhook delivered only when it receives a 200 response. If you return 200 before the DB transaction commits, and the commit then fails (DB error, connection lost), you've told Stripe "I got it" but your system never processed it. Stripe will not retry. The booking stays in `PAYMENT_PENDING` forever. The `@Transactional` boundary must wrap the entire processing logic, and the 200 response must be sent only after the method returns (i.e., after the transaction commits).

**Exact Fix:**  
Ensure `WebhookController` calls `paymentService.handleStripeWebhook()` which is annotated `@Transactional`. The controller method itself must NOT be `@Transactional` — the transaction commits before the controller returns the `ResponseEntity`:
```java
// WebhookController.java — no @Transactional here:
@PostMapping("/webhooks/stripe")
public ResponseEntity<Void> handleWebhook(
    @RequestBody String payload,
    @RequestHeader("Stripe-Signature") String sigHeader) {

    try {
        paymentService.handleStripeWebhook(payload, sigHeader); // @Transactional inside
        return ResponseEntity.ok().build(); // ← sent AFTER transaction commits
    } catch (SignatureVerificationException e) {
        return ResponseEntity.status(400).build();
    } catch (Exception e) {
        log.error("Webhook processing failed", e);
        return ResponseEntity.status(500).build(); // ← Stripe will retry on 5xx
    }
}
```
Stripe retries on 4xx/5xx (except 400 signature failures). Returning 500 on unexpected errors is intentional — it triggers a Stripe retry.

---

### Fix 9.2 — Webhook Idempotency With Concurrent Delivery Guard
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Section 2, Day 9 → Stripe webhook idempotency  
**Affects:** `PaymentService.handleStripeWebhook()`, `ProcessedStripeEvent` entity

**Why:**  
The plan checks `processedEventRepository.existsByStripeEventId()` before processing. But if Stripe delivers the same event twice within milliseconds (concurrent delivery), two threads could both pass the `existsBy` check simultaneously, both proceed to process, and both try to transition the state machine. The application-level check is not sufficient alone. The DB unique constraint is the hard guard.

**Exact Fix:**  
In `V6__create_payments_and_refunds.sql`, add:
```sql
CREATE TABLE processed_stripe_events (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stripe_event_id UNIQUE (stripe_event_id)
);
```
In `PaymentService.handleStripeWebhook()`:
```java
@Transactional
public void handleStripeWebhook(String payload, String sigHeader) {
    Event stripeEvent = verifySignature(payload, sigHeader); // throws on bad sig

    try {
        // Try to insert — if already exists, unique constraint throws:
        processedStripeEventRepository.save(
            ProcessedStripeEvent.builder()
                .stripeEventId(stripeEvent.getId())
                .processedAt(Instant.now())
                .build()
        );
    } catch (DataIntegrityViolationException e) {
        // Duplicate delivery — idempotent: already processed, return silently
        log.info("Duplicate Stripe event ignored: {}", stripeEvent.getId());
        return;
    }

    // Process the event — only reached once per stripe_event_id:
    switch (stripeEvent.getType()) {
        case "checkout.session.completed" -> handlePaymentSuccess(stripeEvent);
        case "checkout.session.expired"   -> handlePaymentFailure(stripeEvent);
    }
}
```
The `DataIntegrityViolationException` catch is the concurrent delivery guard — the DB constraint is the source of truth, not the application-level check.

---

## DAY 10 — RabbitMQ Publishers + Notification Listeners + QR Generation

### Fix 10.1 — Add `DENY_REFUND` Notification Action
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 10 → RabbitMQ publishers + state machine actions  
**Affects:** `BookingStateMachineConfig`, `REFUND_REQUESTED → REFUND_DENIED` transition

**Why:**  
The `REFUND_REQUESTED → REFUND_DENIED` transition has no action. When a user's refund is denied (< 3 days before event), they receive no notification — the booking silently changes state. The user would have to poll their booking status to discover the denial. A notification action that publishes a `RefundDeniedEvent` to RabbitMQ triggers an email explaining the refund policy.

**Exact Fix:**  
Create `NotifyRefundDeniedAction.java`:
```java
@Component
@RequiredArgsConstructor
public class NotifyRefundDeniedAction implements Action<BookingState, BookingEvent> {

    private final RabbitTemplate rabbitTemplate;
    private final BookingRepository bookingRepository;

    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            RefundDeniedEvent event = RefundDeniedEvent.builder()
                .bookingId(bookingId)
                .userId(booking.getUser().getId())
                .userEmail(booking.getUser().getEmail())
                .eventName(booking.getEvent().getTitle())
                .build();
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                "refund.denied",
                event
            );
        });
    }
}
```
Wire in `BookingStateMachineConfig`:
```java
.withExternal()
    .source(BookingState.REFUND_REQUESTED)
    .target(BookingState.REFUND_DENIED)
    .event(BookingEvent.DENY_REFUND)
    .action(notifyRefundDeniedAction)  // ← add this
```

---

### Fix 10.2 — Offload QR Generation to Async Queue
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 10 → `GenerateQRCodeAction` in state machine  
**Affects:** `GenerateQRCodeAction`, new `ticket.generation.queue`

**Why:**  
`GenerateQRCodeAction` runs synchronously inside the Stripe webhook handler — it generates a QR code (ZXing encoding) for every ticket in the booking before the webhook returns. A group booking of 50 tickets = 50 synchronous ZXing encodings in one HTTP request. This delays the webhook response, risks Stripe retrying if it exceeds Stripe's 30-second timeout, and blocks the webhook thread. QR generation belongs on an async queue.

**Exact Fix:**  
In `RabbitMQConfig.java`, declare a new queue:
```java
public static final String TICKET_GENERATION_QUEUE = "ticket.generation.queue";
public static final String TICKET_GENERATION_DLQ   = "ticket.generation.dlq";

@Bean
public Queue ticketGenerationQueue() {
    return QueueBuilder.durable(TICKET_GENERATION_QUEUE)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", TICKET_GENERATION_DLQ)
        .build();
}
```
In `GenerateQRCodeAction.java` — publish instead of generating inline:
```java
@Override
public void execute(StateContext<BookingState, BookingEvent> context) {
    Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
    // Publish async — do NOT generate QR codes here:
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.TICKET_GENERATION_QUEUE,
        GenerateQRCodesCommand.builder().bookingId(bookingId).build()
    );
}
```
Create `QRCodeGenerationListener.java` that consumes from `ticket.generation.queue` and generates the QR codes for all tickets in the booking. The confirmation page polls `GET /api/bookings/{id}` and shows a loading state until QR codes are present on all tickets.

---

## DAY 11 — State Machine Full Configuration

### Fix 11.1 — Add `CANCELLED` State for Organizer Event Cancellation
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 11 → `BookingStateMachineConfig`, `BookingState` enum  
**Affects:** `BookingState`, state machine config, `EventService`

**Why:**  
If an organizer cancels an event (`DELETE /api/events/{id}` or a `cancelEvent()` method), all existing `CONFIRMED` and `RESERVED` bookings for that event have no defined lifecycle path. Without a `CANCELLED` state, these bookings hang in `CONFIRMED` forever. Users don't get refunds, don't get notifications, and don't know the event is cancelled. This is the most user-visible feature gap in the state machine.

**Exact Fix:**  
Add to `BookingState` enum:
```java
CANCELLED
```
Add to `BookingEvent` enum:
```java
EVENT_CANCELLED
```
Add to state machine config (these transitions must be reachable from multiple states):
```java
// CONFIRMED bookings get full refund + notification:
.and().withExternal()
    .source(BookingState.CONFIRMED)
    .target(BookingState.CANCELLED)
    .event(BookingEvent.EVENT_CANCELLED)
    .action(cancelBookingAction)  // issues full Stripe refund + notifies user

// RESERVED bookings release seats (no payment to refund):
.and().withExternal()
    .source(BookingState.RESERVED)
    .target(BookingState.CANCELLED)
    .event(BookingEvent.EVENT_CANCELLED)
    .action(releaseSeatsAction)

// PAYMENT_PENDING bookings cancel payment session:
.and().withExternal()
    .source(BookingState.PAYMENT_PENDING)
    .target(BookingState.CANCELLED)
    .event(BookingEvent.EVENT_CANCELLED)
    .action(cancelPaymentSessionAction)
```
Create `CancelBookingAction.java` that calls `stripeService.refundFullAmount()` and publishes `EventCancelledNotification` to RabbitMQ. In `EventService.cancelEvent()`, send `EVENT_CANCELLED` to all non-terminal bookings for that event.

---

### Fix 11.2 — Clarify `RELEASE` Event Caller
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 2, Day 11 → State machine transition config  
**Affects:** `BookingStateMachineConfig`, `ReservationExpirationJob`

**Why:**  
The transitions `EXPIRED → RELEASE → AVAILABLE` and `PAYMENT_FAILED → RELEASE → AVAILABLE` use a `RELEASE` event with no documented caller. This creates an orphan transition — it exists in the config but nothing sends it. The `AVAILABLE` target state suggests these transitions exist to clean up the booking record after a failed/expired flow, but the mechanism is unclear.

**Exact Fix:**  
Decide: does `RELEASE` represent a distinct state transition needed downstream, or is `EXPIRED` and `PAYMENT_FAILED` already terminal enough?  
**Recommended decision:** Remove the `RELEASE` event and make `EXPIRED` and `PAYMENT_FAILED` the terminal cleanup states. Seat release is handled by `ReleaseSeatsAction` in the transition TO those states. The `AVAILABLE` "state" is not a booking state — it's the tier's inventory state. Rename `BookingState.AVAILABLE` to `BookingState.PENDING` or remove it from the booking state machine entirely (the tier's `available_count` in Redis/DB is the availability record).  

If `RELEASE` is intentional for a future manual admin trigger, document it explicitly:
```java
// In BookingStateMachineConfig — add a Javadoc comment:
/**
 * RELEASE event: sent manually by an admin via POST /api/admin/bookings/{id}/release
 * to unblock a booking stuck in EXPIRED or PAYMENT_FAILED state.
 * Not triggered automatically — requires admin intervention.
 */
.and().withExternal()
    .source(BookingState.EXPIRED)
    .target(BookingState.AVAILABLE)
    .event(BookingEvent.RELEASE)
```

---

## DAY 12 — Pricing Engine + Refund Logic + Waitlist

### Fix 12.1 — Add `refund_denial_reason` Field for User Transparency
**Severity:** 🟢 GOOD PRACTICE  
**Original Plan Location:** Section 2, Day 12 → `RefundService`, `Booking` entity  
**Affects:** `Booking` entity, `RefundService`, `BookingResponse` DTO

**Why:**  
When a refund is denied (< 3 days before event), the user's booking silently moves to `REFUND_DENIED`. Even with the notification email added in Fix 10.1, the user should be able to see the reason on their booking page. This requires one extra field.

**Exact Fix:**  
In Flyway (add to `V8__add_event_features.sql` or create `V10__add_refund_denial.sql`):
```sql
ALTER TABLE bookings ADD COLUMN refund_denial_reason VARCHAR(500) NULL;
```
In `Booking.java`:
```java
private String refundDenialReason;
```
In `RefundService` when denying:
```java
booking.setRefundDenialReason(
    "Refund not eligible: event starts in less than 3 days. " +
    "Event date: " + booking.getEvent().getStartDate() + ". " +
    "Refund request received: " + Instant.now()
);
```
In `BookingResponse` DTO, include `refundDenialReason` so the user dashboard can display it.

---

## DAYS 16–17 — Test Coverage + Load Testing

### Fix 16.1 — Test `reserveSeat()` Lua Script Floor Guard Specifically
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Section 2, Day 16 → Test coverage push  
**Affects:** `InventoryServiceTest`

**Why:**  
Fix 5.1 changed `reserveSeat()` from a plain `DECR` to a Lua script. This new behavior — rejecting decrements that would go below zero — must be explicitly tested. Without this test, the Lua script could have a syntax error or logic bug that goes undetected.

**Exact Fix:**  
In `InventoryServiceTest.java`, add these test cases (use Testcontainers Redis for real behavior):
```java
@Test
@DisplayName("reserveSeat: should return false when available count is 0 (floor guard)")
void reserveSeat_whenCountIsZero_shouldReturnFalse() {
    // Arrange: set inventory to 0
    inventoryService.setAvailableCount(tierId, 0);

    // Act:
    boolean result = inventoryService.reserveSeat(tierId, 1);

    // Assert:
    assertThat(result).isFalse();
    assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(0); // count not modified
}

@Test
@DisplayName("reserveSeat: 100 concurrent threads on 50-seat tier → exactly 50 succeed")
void reserveSeat_concurrent100Threads_exactly50Succeed() throws InterruptedException {
    // Arrange: 50 seats
    inventoryService.setAvailableCount(tierId, 50);
    ExecutorService executor = Executors.newFixedThreadPool(100);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(100);

    // Act: 100 threads all try to reserve 1 seat
    for (int i = 0; i < 100; i++) {
        executor.submit(() -> {
            try {
                if (inventoryService.reserveSeat(tierId, 1)) successes.incrementAndGet();
                else failures.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await(10, TimeUnit.SECONDS);

    // Assert:
    assertThat(successes.get()).isEqualTo(50);
    assertThat(failures.get()).isEqualTo(50);
    assertThat(inventoryService.getAvailableCount(tierId)).isEqualTo(0);
}
```

---

## CROSS-CUTTING — Applies to All Days

### Fix CC-1 — Add `X-Correlation-ID` Request Header Propagation
**Severity:** 🟢 GOOD PRACTICE  
**Original Plan Location:** Section 4 → `common/config/`, `common/util/`  
**Applies:** From Day 2 onward on all service interactions

**Why:**  
A booking request that flows through BookingService → Redis → RabbitMQ → NotificationListener → email produces log lines across multiple classes. Without a shared ID, you cannot grep a single request's full lifecycle in logs. A correlation ID ties all of these lines together.

**Exact Fix:**  
Create `CorrelationIdFilter.java` in `common/config/`:
```java
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_ID_HEADER))
            .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```
In RabbitMQ message publishing, include the correlation ID as a message header:
```java
rabbitTemplate.convertAndSend(exchange, routingKey, event, message -> {
    message.getMessageProperties().setHeader(
        "X-Correlation-ID", MDC.get(CorrelationIdFilter.MDC_KEY)
    );
    return message;
});
```
In RabbitMQ listeners, restore the MDC at the start of `@RabbitListener` methods:
```java
@RabbitListener(queues = BOOKING_CONFIRMED_QUEUE)
public void onBookingConfirmed(BookingConfirmedEvent event, Message message) {
    String correlationId = (String) message.getMessageProperties()
        .getHeader("X-Correlation-ID");
    MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
    try {
        // ... process event
    } finally {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }
}
```

---

### Fix CC-2 — Never Use Magic Numbers — Named Constants for All Business Rules
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 13, Clean Code checklist  
**Applies:** From Day 1 onward

**Why:**  
The business rules in this system — 5-minute reservation window, 300-second lock TTL, 80% capacity threshold for dynamic pricing, 30% group discount trigger, 7/3-day refund tiers — appear as raw numbers scattered across the codebase. When a rule changes, you change one constant instead of hunting for every occurrence.

**Exact Fix:**  
Create `BusinessConstants.java` in `common/util/`:
```java
public final class BusinessConstants {

    private BusinessConstants() {}

    // Reservation
    public static final long RESERVATION_TTL_SECONDS      = 300L;   // 5 minutes
    public static final long LOCK_TTL_SECONDS             = 300L;   // same as reservation

    // Pricing
    public static final int  EARLY_BIRD_DAYS_THRESHOLD    = 30;     // days before event
    public static final double EARLY_BIRD_DISCOUNT        = 0.50;   // 50% off
    public static final int  GROUP_DISCOUNT_MIN_QUANTITY  = 5;      // min tickets for group rate
    public static final double GROUP_DISCOUNT_RATE        = 0.10;   // 10% off
    public static final double DYNAMIC_PRICING_THRESHOLD  = 0.80;   // 80% sold triggers surge
    public static final double DYNAMIC_PRICING_SURGE      = 0.25;   // 25% increase

    // Refund tiers
    public static final int  FULL_REFUND_DAYS_THRESHOLD   = 7;      // >= 7 days = full refund
    public static final int  PARTIAL_REFUND_DAYS_THRESHOLD = 3;     // 3–6 days = 50% refund
    public static final double PARTIAL_REFUND_RATE        = 0.50;   // 50%

    // Scheduler
    public static final int  EXPIRY_JOB_INTERVAL_MS       = 30_000; // 30 seconds
}
```

---

## PRE-WEEK 3 CHECKPOINT — Before Day 15

### Fix PW3-1 — Stripe Account + Railway Account Setup (Do Before Day 9)
**Severity:** 🔴 CRITICAL  
**Original Plan Location:** Not in plan — gap identified by analysis  
**When to action:** Day 1 or Day 2 (not Day 9 when you need them)

**Why:**  
The plan assumes on Day 9 that a Stripe test account and Railway account exist. Stripe account creation is instant but setting up the dashboard, getting test API keys, and configuring the webhook endpoint takes 30–60 minutes. Railway onboarding requires email verification and project setup. If you do this for the first time on Day 9 (a 7-hour day already at capacity), it will blow your schedule.

**Exact Fix:**  
On Day 1 evening (during the 1-hour TDD/git block), take 20 minutes to:
1. Create a Stripe account at stripe.com → get `sk_test_` and `pk_test_` keys → save to `application-local.yml`
2. Install the Stripe CLI: `stripe login` then
   `stripe listen --forward-to localhost:8088/api/v1/payments/webhook`
   (for local webhook testing). Put the `whsec_` it prints into `.env` as
   `STRIPE_WEBHOOK_SECRET`.

   > **Corrected Day 24.** This step previously named
   > `localhost:8080/api/webhooks/stripe` — wrong port *and* wrong path. The
   > real endpoint is `:8088` and `/api/v1/payments/webhook`. Following the old
   > line would silently deliver nothing, which is precisely the failure mode in
   > Fix 24-webhook.
   >
   > **Status: NOT DONE.** The CLI is not installed and the Stripe account has
   > zero webhook endpoints registered. This fix was marked applied in
   > `PROGRESS.md` while neither half of it was true.
3. Create a Railway account at railway.app → create a new project (empty) → note the project ID
4. Add all environment variable names (empty values) to Railway now so they're ready to fill on Day 18

---

## FINAL CHECKLIST — Day 21 (Phase 1A Close)

Before tagging `v1.0.0`, verify all critical fixes were applied:

- [ ] `Instant` used for all time-sensitive fields (Fix 1.1)
- [ ] PostgreSQL ENUM for roles (Fix 1.2)
- [ ] Lua floor guard in `reserveSeat()` (Fix 5.1)
- [ ] TOCTOU double-check inside lock in `reserveTickets()` (Fix 8.1)
- [ ] CHECK_IN guard implemented (Fix 8.2)
- [ ] Expiry job distributed lock (Fix 8.3)
- [ ] Webhook 200 only after DB commit (Fix 9.1)
- [ ] Webhook idempotency with concurrent guard (Fix 9.2)
- [ ] DENY_REFUND notification action (Fix 10.1)
- [ ] QR generation offloaded to async queue (Fix 10.2)
- [ ] CANCELLED state added to state machine (Fix 11.1)
- [ ] RELEASE event caller documented or removed (Fix 11.2)
- [ ] `refund_denial_reason` field added (Fix 12.1)
- [ ] Lua floor guard tested with concurrency test (Fix 16.1)
- [ ] Correlation ID propagation across HTTP + RabbitMQ (Fix CC-1)
- [ ] `BusinessConstants.java` used throughout — no magic numbers (Fix CC-2)
- [ ] Stripe + Railway accounts created before Day 9 (Fix PW3-1)

---

## MISSING FIXES — Not in Original Overlay (Discovered via Full Audit)

The following fixes were referenced in session prompts or verification checklists but never documented in this overlay file. They must be applied during their respective days.

---

### Fix E-001 — Register Form Must NOT Send `role` Field
**Severity:** 🔴 CRITICAL (Role escalation vulnerability)
**Day:** Day 15 (frontend register form) / verified Day 21
**File:** `frontend/src/app/auth/register/page.tsx`, `AuthController.java`

**Why:** If the register form includes a `role` field in its payload, any user can self-assign `ORGANIZER` or attempt `ADMIN`. The backend `RegisterRequest` DTO must not include a `role` field, or if it does, the backend must forcibly set `role = USER` regardless of what is sent.

**Exact Fix:**
Frontend register payload must be exactly `{ firstName, lastName, email, password }` — NO `role` field. Verify with DevTools Network tab during Day 21 smoke test:
```json
// CORRECT — no role field:
{ "firstName": "Ali", "lastName": "Ahmed", "email": "...", "password": "..." }

// WRONG — role escalation vector:
{ "firstName": "Ali", ..., "role": "ORGANIZER" }
```
Backend: if `RegisterRequest` has a `role` field at all, annotate it `@JsonIgnore` or remove it entirely. Role defaults to `USER` in the service layer.

---

### Fix E-008 — GlobalExceptionHandler Must Have `Exception.class` Catch-All
**Severity:** 🟠 HIGH
**Day:** Day 2 or any day where GlobalExceptionHandler is created
**File:** `src/main/java/com/ticketing/common/exception/GlobalExceptionHandler.java`

**Why:** Without a `Exception.class` catch-all handler, any unhandled `RuntimeException` propagates as a raw Spring Whitelabel Error Page or 500 with a stack trace in the response body — exposing internal implementation details. The catch-all must return a generic `ApiResponse.failure()` with no stack trace, and MUST log the exception with `log.error("Unexpected exception", ex)` so the stack trace appears in server logs only.

**Exact Fix:**
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception — correlationId={}", 
        MDC.get("correlationId"), ex);  // ex is 3rd arg when msg has params
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.failure("An unexpected error occurred. Please try again."));
    // NEVER return ex.getMessage() or ex.getClass().getName() — information exposure
}
```

---

### Fix 7.1 — Add `@Version` Optimistic Locking to Booking Entity
**Severity:** 🟡 IMPORTANT
**Day:** Day 8 (Booking entity creation)
**File:** `Booking.java`

**Why:** The Day 21 verification table checks `SELECT version FROM bookings WHERE id=1 → increments on each update` — but this column is never explicitly documented as a fix to implement. Without `@Version`, concurrent updates to the same booking (e.g., two webhook deliveries arriving simultaneously) are not protected by optimistic locking, and the `@Retryable` on `checkIn()` (Fix M-001) has nothing to retry against.

**Exact Fix:**
```java
// In Booking.java entity:
@Version
@Column(nullable = false)
private Long version = 0L;
```
In `V5__create_bookings_and_tickets.sql`:
```sql
version BIGINT NOT NULL DEFAULT 0,
```
The `@Version` field is automatically incremented by Hibernate on every `save()` and checked on concurrent updates — `ObjectOptimisticLockingFailureException` is thrown if another transaction committed a newer version first.

---

### Fix 16B.2 — `payments_booking_id_key` UNIQUE Violation on Payment Upsert
**Severity:** 🔴 CRITICAL
**Day:** Day 9 (PaymentService implementation)
**File:** `PaymentService.java`, `Payment.java`, Flyway migration

**Why:** The `payments` table has a UNIQUE constraint on `booking_id` (one payment per booking). When Stripe fires a retry webhook (e.g., `checkout.session.completed` delivered twice for the same session), the idempotency guard in Fix 9.2 handles the `ProcessedStripeEvent` duplicate. However, if the first webhook partially succeeded (saved the payment record) and then failed before returning 200 to Stripe, Stripe retries. The retry passes the stripe event idempotency check (because the first record was never saved to `processed_stripe_events` due to the partial failure), then tries to insert a new `Payment` record — which fails with `payments_booking_id_key` UNIQUE violation. This is distinct from Fix 9.2.

**Exact Fix:** In `PaymentService`, use upsert semantics (find-or-create) for the Payment record:
```java
Payment payment = paymentRepository.findByBookingId(booking.getId())
    .orElseGet(() -> Payment.builder().booking(booking).build());

payment.setStripeSessionId(sessionId);
payment.setAmount(amount);
payment.setStatus(PaymentStatus.COMPLETED);
payment.setPaidAt(Instant.now());
paymentRepository.save(payment);  // INSERT on first save, UPDATE on retry
```
This is idempotent — retrying the same sessionId updates the same record rather than inserting a duplicate.

---

### Fix 16B-missing — BookingControllerTest (9 Tests Required by Day 16B)
**Severity:** 🔴 CRITICAL (required for 80% coverage gate)
**Day:** Day 16
**File:** `src/test/java/com/ticketing/booking/BookingControllerTest.java`

**Why:** CLAUDE.md explicitly calls out `Fix 16B-missing` as a pending critical fix. Without this test class, `BookingController` has zero coverage, failing the 80% JaCoCo gate.

**Required test names (TDD naming convention):**
```
bookingController_whenReserveValid_shouldReturn201
bookingController_whenReserveUnauthenticated_shouldReturn401
bookingController_whenReserveInvalidBody_shouldReturn400
bookingController_whenGetBookingOwner_shouldReturn200
bookingController_whenGetBookingOtherUser_shouldReturn403
bookingController_whenCheckInAsOrganizer_shouldReturn200
bookingController_whenCheckInAsUser_shouldReturn403
bookingController_whenCancelAsOwner_shouldReturn200
bookingController_whenCancelAsNonOwner_shouldReturn403
```

**Mandatory test setup:**
```java
@WebMvcTest(controllers = BookingController.class)
@Import(TestSecurityConfig.class)        // MANDATORY
class BookingControllerTest {
    @MockitoBean BookingService bookingService;  // @MockitoBean not @MockBean (Spring Boot 3.4+)
    @MockitoBean JwtService jwtService;          // MANDATORY — JwtFilter requires this
    // DO NOT use addFilters = false
}
```

---

### Fix M-008 — Clarify Token Storage Strategy (sessionStorage vs cookie vs Zustand)
**Severity:** 🟡 IMPORTANT
**Day:** Day 15 / verified Day 21
**Files:** `authStore.ts`, `login/page.tsx`, `middleware.ts`

**Why:** There is a three-way contradiction across documents about where the JWT lives:
1. `14_frontend.md` shows Zustand in-memory (no persistence)
2. `18_debugging_report.md` (Bug 2 fix) shows token written to `document.cookie` for Next.js middleware
3. Day 21 smoke test says "JWT in sessionStorage (NOT localStorage)"

The intended architecture for Phase 1A must be explicitly chosen:

**Chosen architecture (M-008 interim):**
- Zustand `authStore` with `persist` middleware using `sessionStorage` as the storage adapter
- Cookie also written on login (for Next.js server-side middleware route protection)
- Rationale: sessionStorage is cleared on tab close (safer than localStorage), cookie enables SSR auth guards

**Exact Fix:**
```typescript
import { persist, createJSONStorage } from 'zustand/middleware'

export const useAuthStore = create(
    persist(
        (set) => ({
            token: null,
            user: null,
            setAuth: (token, user) => set({ token, user }),
            clearAuth: () => set({ token: null, user: null }),
        }),
        {
            name: 'auth-storage',
            storage: createJSONStorage(() => sessionStorage),  // NOT localStorage
        }
    )
)
```

---

### Fix M-001 (Addendum) — `@Retryable` Must NOT Apply to `reserveTickets()`
**Severity:** 🔴 CRITICAL
**Day:** Day 16 / any time BookingService is modified
**File:** `BookingService.java`

**Why:** Already documented in Day 16 prompt, but NOT in this overlay file. Adding `@Retryable` to `reserveTickets()` causes Redis inventory to be decremented multiple times while only one booking is created — a permanent invisible inventory undercount. The Lua floor guard prevents the count going negative but the second attempt still decrements it.

**Exact rule:** `@Retryable` is allowed on `checkIn()` ONLY. It is FORBIDDEN on any method that touches Redis inventory counters.

---

## FINAL CHECKLIST — Day 21 (Phase 1A Close) [UPDATED]

Before tagging `v1.0.0`, verify all critical fixes were applied:

**Original overlay fixes:**
- [ ] `Instant` used for all time-sensitive fields (Fix 1.1)
- [ ] PostgreSQL ENUM for roles (Fix 1.2)
- [ ] Lua floor guard in `reserveSeat()` (Fix 5.1)
- [ ] TOCTOU double-check inside lock in `reserveTickets()` (Fix 8.1)
- [ ] CHECK_IN guard implemented (Fix 8.2)
- [ ] Expiry job distributed lock (Fix 8.3)
- [ ] Webhook 200 only after DB commit (Fix 9.1)
- [ ] Webhook idempotency with concurrent guard (Fix 9.2)
- [ ] DENY_REFUND notification action (Fix 10.1)
- [ ] QR generation offloaded to async queue (Fix 10.2)
- [ ] CANCELLED state added to state machine (Fix 11.1)
- [ ] RELEASE event caller documented or removed (Fix 11.2)
- [ ] `refund_denial_reason` field added (Fix 12.1)
- [ ] Lua floor guard tested with concurrency test (Fix 16.1)
- [ ] Correlation ID propagation across HTTP + RabbitMQ (Fix CC-1)
- [ ] `BusinessConstants.java` used throughout — no magic numbers (Fix CC-2)
- [ ] Stripe + Railway accounts created before Day 9 (Fix PW3-1)

**Newly documented missing fixes:**
- [ ] Register form sends no `role` field (Fix E-001)
- [ ] GlobalExceptionHandler has `Exception.class` catch-all with `log.error(..., ex)` (Fix E-008)
- [ ] `@Version` optimistic locking on `Booking` entity (Fix 7.1)
- [ ] Payment upsert instead of insert on webhook retry (Fix 16B.2)
- [ ] BookingControllerTest — 9 tests passing (Fix 16B-missing)
- [ ] Token storage strategy resolved: sessionStorage via Zustand persist (Fix M-008)
- [ ] `@Retryable` NOT on `reserveTickets()` — only on `checkIn()` (Fix M-001 addendum)

**Session prompt bugs (from 20_session_prompt_review.md):**
- [ ] `@Transactional` removed from `ConcurrentBookingTest` (BUG-D16-1)
- [ ] `actions/checkout@v4` corrected in ALL GitHub Actions YAML files (BUG-D18-1)
- [ ] `RateLimitFilter` uses constructor injection, not `@Autowired` (BUG-D20-1)
- [ ] Rate limit counter is atomic Lua script, not two-step INCR+EXPIRE (BUG-D20-2)
- [ ] `status().isNot(429)` replaced with valid MockMvc assertion (BUG-D20-3)
- [ ] Actuator `permitAll()` replaced with health-only public access (BUG-D20-6)

---

---

**Day 24 — payment reliability (applied):**
- [x] Reconciliation fallback confirms a paid booking from the success redirect (Fix 24-webhook)
- [x] `confirmPaidBooking` extracted so webhook and reconciliation cannot drift (Fix 24-webhook)
- [x] `@MockitoBean` added for the new controller collaborator (Fix 24-slice)
- [x] `.no-scrollbar` defined; stray vertical scrollbar on the rail removed (Fix 24-scrollbar)
- [x] `--color-success` token family added; raw hex 41 to 1 (Fix 24-trackA)
- [x] Sales chart derives from real data instead of hardcoded coordinates (Fix 24-trackA)
- [x] Fix PW3-1 corrected — CLI not installed, and its command named the wrong endpoint (Fix 24-clidoc)

**Still outstanding — environment, not code:**
- [ ] Install the Stripe CLI and run `stripe listen --forward-to localhost:8088/api/v1/payments/webhook`
- [ ] Set `STRIPE_WEBHOOK_SECRET` in `.env` (currently unset, so compose falls back to `whsec_placeholder`)
- [ ] Register a webhook endpoint in the Stripe Dashboard for production — the account has zero
- [ ] Refund the two duplicate charges on the twice-paid booking (`pi_3U6rvL`, `pi_3U6ruj`)
- [ ] Frontend must send `Idempotency-Key` on `POST /api/v1/bookings` before `app.rate-limit.enabled=true` reaches production (carried from Day 20)

## DAY 24 — Payment Reliability + UI Enhancement (Discovered in Use)

Found by the user during an ordinary booking, not by a checklist. Documented here
because the payment one is a design gap that survives redeployment, not a one-off
environment glitch.

---

### Fix 24-webhook — Paid Bookings Stranded at `PAYMENT_PENDING`
**Severity:** CRITICAL (money taken, booking still fails)
**Day:** Day 24
**Files:** `PaymentReconciliationService.java` (new), `WebhookService.java`,
`BookingController.java`, `frontend/src/app/bookings/[id]/confirmation/page.tsx`

**Symptom:** A booking paid for through Stripe stayed `PAYMENT_PENDING` on the
dashboard. Stripe reported the session `paid` with a real payment intent; the
application never advanced the booking.

**Why:** `WebhookService.handlePaymentSuccess` is the *only* exit from
`PAYMENT_PENDING`, and it fires solely on Stripe's asynchronous
`checkout.session.completed`. The Stripe account had **zero webhook endpoints
registered**, and Stripe cannot reach `localhost:8088` in any case, so that event
had never once been delivered.

This is worse than a stuck status. `ReservationExpirationJob` sweeps stale
`PAYMENT_PENDING` to `PAYMENT_FAILED` and releases the seats — so the card is
charged and the booking still fails. It also compounds: the stuck state leaves a
*Resume* action in the UI which opens a **new** checkout session for the same
booking. One booking was charged twice (EGP 1,500 x 2) and ended `CANCELLED`
with two live payments.

**Root cause classification:** the proximate cause is environmental (no endpoint
registered). The durable defect is that confirmation depends on **one**
asynchronous signal with **no fallback** — which fails identically in production
whenever a webhook is delayed or lost. Fix 23-bug2 had already shown webhooks can
be lost in this system.

**Exact Fix:** Stripe returns `session_id` on the success redirect precisely so
the application can verify synchronously. The confirmation page was receiving it
and ignoring it.

- Extract the confirmation body of `handlePaymentSuccess` into
  `WebhookService.confirmPaidBooking(Session, correlationId)`. Extracted, **not
  reimplemented** — two copies of "mark this booking paid" would drift, and the
  difference would only surface once a webhook went missing.
- `PaymentReconciliationService.reconcile(bookingId, userId)` retrieves the
  session and, when `payment_status == "paid"`, delegates to that same method.
- `POST /api/v1/bookings/{id}/sync-payment` exposes it; the confirmation page
  calls it whenever the booking reads `PAYMENT_PENDING`, then re-reads.

**Properties that matter:**
- **Idempotent** — the `PAYMENT_PENDING` guard makes a second call a no-op, which
  is what keeps a webhook-and-reconciliation race harmless.
- **Cheap on the common path** — anything already settled returns without
  contacting Stripe, and this runs on every confirmation-page visit.
- **Ownership enforced object-level**, independent of endpoint authorization.
- **Fails soft** — a Stripe outage is logged and swallowed, never fails the page.
- **Logs at WARN when it steps in**, so a missing webhook stays visible in
  operations instead of being silently papered over.

The webhook remains the primary path. Reconciliation only closes the gap when it
does not arrive, and cannot replace it: `checkout.session.expired` and async
payment failures have no redirect to piggyback on.

**Verification:** a real Stripe test-mode purchase with **no webhook relayed at
all** reached the confirmation page as `CONFIRMED` with its QR ticket issued
(booking 564, intent `pi_3U6sjI`), with the WARN line present in the backend log.

---

### Fix 24-slice — New Controller Dependency Breaks the `@WebMvcTest` Slice
**Severity:** IMPORTANT (13 tests fail to load context)
**Day:** Day 24
**File:** `src/test/java/com/ticketing/booking/controller/BookingControllerTest.java`

**Why:** `@WebMvcTest` still constructs the controller bean, so a constructor
dependency with no corresponding `@MockitoBean` fails context load — and takes
every test in the slice with it, reported as errors rather than failures.

**Exact Fix:** add `@MockitoBean PaymentReconciliationService` alongside the
existing mocks. This is the same trap as the `JwtService` mock already documented
in the test-security section: **any** collaborator added to a controller needs a
matching mock in its slice.

---

### Fix 24-clidoc — Fix PW3-1 Claimed the Stripe CLI Was Installed
**Severity:** GOOD PRACTICE (but it cost real debugging time)
**Day:** Day 24
**File:** `instructions.md`

**Why:** Fix PW3-1 was marked applied: "Stripe account + CLI installed". The CLI
is not installed, and that stale entry is what made the undelivered webhook look
impossible — the local setup appeared to already satisfy webhook delivery.

**Exact Fix:** `instructions.md` now records the CLI as NOT installed, with the
command it is needed for and the consequence of running without it:

    stripe listen --forward-to localhost:8088/api/v1/payments/webhook

Without it, `checkout.session.completed` never arrives and paid bookings stay
`PAYMENT_PENDING` until the expiry job marks them `PAYMENT_FAILED`.

Production needs a webhook endpoint registered in the Stripe Dashboard. The
account currently has none, so live checkout would fail exactly this way.

---

### Fix 24-scrollbar — `.no-scrollbar` Referenced but Never Defined
**Severity:** IMPORTANT (visible defect on the landing page)
**Day:** Day 24
**Files:** `frontend/src/app/globals.css`, `frontend/src/app/page.tsx`

**Why:** The featured-events rail carried `className="no-scrollbar"`, but that
utility was **never defined anywhere**, so the browser painted its default bar.
A second, stray vertical scrollbar appeared alongside it because per CSS spec a
container that is not `visible` on one axis computes to `auto` on the other — the
reveal transform and card hover-lift then overflowed vertically.

**Exact Fix:** define `.no-scrollbar`, and add `.scroll-rail` which pins
`overflow-y: hidden`, adds a slim gradient thumb, scroll snapping, and padding for
the hover lift. Edge fades sized to the gutter so they do not wash the first and
last card.

---

### Fix 24-trackA — Design Tokens and a Chart That Ignored Its Data
**Severity:** GOOD PRACTICE
**Day:** Day 24
**Files:** `globals.css`, `src/lib/chart-theme.ts`, `components/organizer/SalesChart.tsx`

**Why:** There was a `--color-error` but **no `--color-success`**, which is why a
success green (`#137333` / `#e6f4ea`) was hardcoded in three separate files. Raw
hex in the frontend totalled 41 occurrences — most of them literal duplicates of
tokens that already existed (`#630ed4` is `--color-primary`).

Separately: the organizer "Sales Over Time" chart was **not a chart**. Its SVG
path coordinates were hardcoded, so it drew the same rising curve regardless of
the data.

**Exact Fix:** add the `--color-success` token family; replace token-duplicate
literals (41 to 1, the survivor being a `-webkit-autofill` shadow that needs a
literal); extract chart colours to `chart-theme.ts`; rebuild the chart as
`SalesChart.tsx` derived from real events, with an explicit empty state so a
zero-revenue organizer sees "No sales yet" rather than a flat line that reads as
broken.

---

## PHASE 1B — Deferred Items and New Issues

The following items were explicitly deferred to Phase 1B or are newly identified from full project audit. They are NOT to be implemented in Phase 1A.

---

### Phase 1B Roadmap

#### H-001 — State Machine API Migration (Spring State Machine 4.x)
**Why deferred:** `StateMachineConfigurerAdapter` is deprecated in Spring State Machine 4.x. The functional API (`StateMachineModelFactory`) is the replacement. Migration requires rewriting `BookingStateMachineConfig` and all guards/actions. Functional — deferred because the deprecated API works correctly.

**Phase 1B target:** Migrate to the builder/functional API, add state machine metrics via Micrometer, add state transition event logging.

---

#### H-002 — Outbox Pattern for Reliable RabbitMQ Publishing
**Why deferred:** Currently, RabbitMQ messages are published inside `@Transactional` service methods — but they are not part of the DB transaction. If the DB commits and then RabbitMQ publish fails (broker down, network partition), the notification email is silently dropped, and the QR code is never generated. The booking is CONFIRMED but the user gets nothing.

**Phase 1B target:** Implement the Transactional Outbox pattern:
1. Save a `OutboxEvent` record to the DB inside the same transaction as the business operation
2. A scheduled `OutboxPoller` reads unprocessed outbox events and publishes them to RabbitMQ
3. Mark the outbox event as `SENT` after successful publish
4. This guarantees at-least-once delivery even across broker restarts

---

#### H-003 — HttpOnly Cookie Authentication (M-008 Full Implementation)
**Why deferred:** Full HttpOnly cookie auth requires the frontend (Vercel) and backend (Railway) to share a domain (e.g., `app.example.com` → Vercel, `api.example.com` → Railway). In Phase 1A, they are on separate Railway/Vercel subdomains — cross-domain cookies with SameSite=Strict are blocked by browsers.

**Phase 1B target:** Set up a custom domain (e.g., `ticketing.example.com`) with a shared apex domain. Backend sets `Set-Cookie: token=...; HttpOnly; Secure; SameSite=Strict; Domain=.example.com`. Frontend reads nothing from storage — the browser attaches the cookie automatically.

---

#### H-004 — Elasticsearch Event Search with CQRS
**Why deferred:** Current search is PostgreSQL full-text (`ILIKE` or `tsvector`). It has no relevance ranking, no fuzzy matching, and scales poorly with large event catalogs.

**Phase 1B target:** Add Elasticsearch 8.x via Spring Data Elasticsearch. Publish `EventCreatedEvent`/`EventUpdatedEvent` to a separate Elasticsearch projection index. `EventSearchController` queries ES, not PostgreSQL. Use CQRS: PostgreSQL is the write model, ES is the read model.

---

#### H-005 — WebSocket Real-Time Seat Availability
**Why deferred:** Current availability is polled via `GET /api/events/{id}` which caches for 5 minutes. This means a user can see "50 seats available" while the last seat is being purchased.

**Phase 1B target:** Implement `@MessageMapping` with STOMP over WebSocket. When `inventoryService.reserveSeat()` decrements, broadcast the new count to all subscribers of the event's topic (`/topic/events/{id}/availability`). Frontend subscribes using `@stomp/stompjs`.

---

#### H-006 — Organizer Multi-Step Event Creation Form (Days 14–15 Incomplete)
**Current state:** The Create Event form only implements Step 1 (Basic Info). Steps 2–4 (Ticket Tiers, Media, Review) are mocked/empty placeholders in the organizer dashboard.

**Phase 1B target:** Implement the full multi-step form with:
- Step 2: Ticket tier builder (add multiple tiers with capacity + price)
- Step 3: Cover image upload to S3/Cloudinary
- Step 4: Review and publish form

---

#### H-007 — Event Image Upload (S3 / Cloudinary)
**Current state:** `Event` entity has `coverImageUrl` field but the upload mechanism does not exist. Events are created without cover images; the frontend falls back to a placeholder.

**Phase 1B target:** Add `POST /api/v1/events/{id}/cover` endpoint accepting `multipart/form-data`. Backend uploads to S3 (AWS SDK) or Cloudinary, stores the resulting public URL in `Event.coverImageUrl`. Frontend renders the actual cover on event cards and detail pages.

---

#### H-008 — Full OWASP/ASVS Compliance Mapping
**Why deferred:** Phase 1A addresses OWASP Top 10 defensively (parameterized queries, JWT auth, rate limiting, CORS, security headers). A formal ASVS L2 compliance audit requires a dedicated security review session mapping every control.

**Phase 1B target:** Run OWASP ZAP passive scan against the Railway deployment, generate a report, address any findings. Map all ASVS L2 controls to implemented mitigations.

---

#### H-009 — Kubernetes + Helm Deployment
**Why deferred:** Phase 1A uses Railway single-instance deployment. Kubernetes adds significant operational complexity with no benefit at the current scale.

**Phase 1B target:** Write `k8s/` manifests: `Deployment`, `Service`, `ConfigMap`, `Secret`, `HorizontalPodAutoscaler`. Write Helm chart. Set up GKE or EKS cluster. Add readiness/liveness probes to the Spring Boot app (using `/actuator/health/readiness` and `/actuator/health/liveness`).

---

#### H-010 — Full Observability Stack (Prometheus + Grafana + Jaeger)
**Why deferred:** Phase 1A uses Actuator + structured JSON logs. Production-grade observability requires distributed tracing (Jaeger), metrics aggregation (Prometheus), and dashboards (Grafana).

**Phase 1B target:** 
- Add Micrometer + Prometheus metrics export at `/actuator/prometheus`
- Wire Spring Boot Actuator to Prometheus scrape config
- Add Grafana dashboards for: booking creation rate, inventory levels, state machine transition latency, RabbitMQ queue depth
- Add Zipkin/Jaeger tracing via `spring-cloud-sleuth` (or Micrometer Tracing)

---

#### H-011 — Waitlist Notification Email Templates
**Current state:** `WaitlistService` notifies the next-in-queue user when a cancellation occurs by publishing to RabbitMQ. But `NotificationService` likely sends a plain-text email or no email at all for waitlist events.

**Phase 1B target:** Create proper HTML email templates using Thymeleaf. Templates for: booking confirmation, refund approved/denied, waitlist notification ("A seat opened up for event X — complete your booking within 15 minutes"), payment failure.

---

#### H-012 — Refund Flow End-to-End Verification
**Current state:** The `RefundService` submits refund requests via Stripe API. The refund webhook (`charge.refunded`) handling is not confirmed to be implemented.

**Phase 1B target:** Verify `charge.refunded` webhook is handled in `WebhookService`, booking state is transitioned to `REFUND_APPROVED`, and the user receives a notification. Add integration test using Stripe CLI: `stripe trigger charge.refunded`.

---

#### H-013 — Payment Retry on Stripe Failure
**Current state:** If Stripe payment fails (`checkout.session.expired`), booking moves to `PAYMENT_FAILED` with no retry path. The user must start over from event discovery.

**Phase 1B target:** Add `POST /api/v1/bookings/{id}/retry-payment` endpoint that: validates booking is in `PAYMENT_FAILED` state, creates a new Stripe Checkout session, transitions back to `PAYMENT_PENDING`. Re-use the distributed lock to prevent concurrent retries.

---

#### H-014 — Admin Dashboard and User Management
**Current state:** ADMIN role exists and is blocked from self-registration (Fix E-001) but there is no admin UI or API beyond what comes from existing controller `@PreAuthorize("hasRole('ADMIN')")` guards.

**Phase 1B target:** Implement `/admin` pages:
- User management (list users, change roles, deactivate)
- Event moderation (force-publish, force-cancel any event)
- System metrics summary (total bookings, revenue, active events)

---

#### NEW-001 — Booking Idempotency-Key Enforcement on Backend
**Discovered via:** Scope analysis HIGH-9 + Day 20 M-002 description

**Why:** The frontend sends `Idempotency-Key: crypto.randomUUID()` on `POST /api/v1/bookings/reserve`. But the backend does not validate or honor this header — it's purely advisory. If the frontend double-submits (network timeout + retry), two bookings are created for the same user/tier/quantity.

**Phase 1B target:** Add Redis-based idempotency key store on the booking endpoint:
1. On receipt of `Idempotency-Key`, check Redis for an existing response stored at `idempotency:{key}`
2. If found, return the cached response immediately (no double booking)
3. If not found, process normally, then cache the response with a 5-minute TTL

---

#### NEW-002 — Organizer Sales Analytics (Real Charts)
**Current state:** Day 15 organizer dashboard has mocked chart data or placeholder charts.

**Phase 1B target:** Wire the organizer analytics to real data:
- `GET /api/v1/organizer/events/{id}/analytics` → return time-series booking data
- Frontend: real Recharts line chart from actual booking timestamps
- Add: revenue breakdown by tier, conversion rate (reserved → confirmed), refund rate

---

#### NEW-003 — Database Connection Pool Tuning
**Current state:** Spring Boot defaults (HikariCP `maximumPoolSize=10`). Under the k6 load tests, connection pool exhaustion may occur under sustained booking load.

**Phase 1B target:** Profile HikariCP metrics via `/actuator/metrics/hikaricp.connections.active`. Tune: `maximum-pool-size` based on Railway instance vCPU count, `connection-timeout`, `idle-timeout`. Add HikariCP health indicator to readiness probe.

---

#### NEW-004 — Booking State Machine Persistence Store
**Current state:** State machine state is rehydrated from the DB `booking.state` field on each request. This works but doesn't take advantage of Spring State Machine's built-in `StateMachineRuntimePersister`.

**Phase 1B target:** Implement `JpaPersistingStateMachineInterceptor` to store the full state machine context (including extended state variables) to a `state_machine_context` table. Benefit: extended state (variables like `bookingId`, `currentUserId`) survives across JVM restarts without manual rehydration logic.

---

*Companion to Phase 1A Sections 2–16 | April 4–24, 2026 | All fixes apply on top of the original plan without replacing it*
