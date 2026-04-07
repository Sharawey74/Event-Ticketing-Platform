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

## DAY 7 — Week 1 Cleanup & Docker Compose

### Fix 7.1 — Enforce Docker Compose `service_healthy` Startup Order
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 11, Section 15 (Troubleshooting #6)  
**Affects:** `docker-compose.yml`, `application.yml`

**Why:**  
Spring Boot starts faster than PostgreSQL/RabbitMQ can accept connections. A simple `depends_on` only waits for the container to start, not be ready. Without explicit health checks and `condition: service_healthy`, Spring Boot throws `Connection refused` and dies on startup.

**Exact Fix:**  
In `docker-compose.yml`, add health checks to infrastructure services:
- **postgres**: `test: ["CMD-SHELL", "pg_isready -U ticketing"]`
- **redis**: `test: ["CMD", "redis-cli", "ping"]`
- **rabbitmq**: `test: ["CMD", "rabbitmq-diagnostics", "ping"]`

In the `app` service `depends_on`, require health:
```yaml
depends_on:
  postgres:
    condition: service_healthy
  redis:
    condition: service_healthy
  rabbitmq:
    condition: service_healthy
```
In `application.yml` (or `application-local.yml`), add HikariCP retry capability:
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000
      initialization-fail-timeout: 60000
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

### Fix 8.4 — Spring Retry for Optimistic Locking Failures
**Severity:** 🟡 IMPORTANT  
**Original Plan Location:** Section 15 (Troubleshooting #5 and #7)  
**Affects:** `BookingService.reserveTickets()`, `@Version` conflicts

**Why:**  
The `@Version` field on `Booking` and `TicketTier` acts as optimistic locking. If two threads bypass Redis or try to update the same booking simultaneously, JPA throws `ObjectOptimisticLockingFailureException`. Under load, this shouldn't just crash out — it should automatically retry the transaction a few times before failing. This is explicitly called out in Troubleshooting #7.

**Exact Fix:**  
1. Ensure the `spring-retry` dependency is in `pom.xml`.
2. Add `@EnableRetry` to `TicketingApplication.java`.
3. Annotate `BookingService.reserveTickets()` with:
```java
@Retryable(
    value = {ObjectOptimisticLockingFailureException.class}, 
    maxAttempts = 3, 
    backoff = @Backoff(delay = 100, multiplier = 2.0)
)
@Transactional
public BookingResponse reserveTickets(...) { ... }
```
This forces the transaction to retry if the inner inventory update hits an optimistic lock clash.

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
2. Install the Stripe CLI: `stripe login` → `stripe listen --forward-to localhost:8080/api/webhooks/stripe` (for local webhook testing)
3. Create a Railway account at railway.app → create a new project (empty) → note the project ID
4. Add all environment variable names (empty values) to Railway now so they're ready to fill on Day 18

---

## FINAL CHECKLIST — Day 21 (Phase 1A Close)

Before tagging `v1.0.0`, verify all critical fixes were applied:

- [ ] `Instant` used for all time-sensitive fields (Fix 1.1)
- [ ] PostgreSQL ENUM for roles (Fix 1.2)
- [ ] Lua floor guard in `reserveSeat()` (Fix 5.1)
- [ ] Docker Compose `service_healthy` enforcement (Fix 7.1)
- [ ] TOCTOU double-check inside lock in `reserveTickets()` (Fix 8.1)
- [ ] CHECK_IN guard implemented (Fix 8.2)
- [ ] Expiry job distributed lock (Fix 8.3)
- [ ] `@Retryable` for Optimistic Locking (Fix 8.4)
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

*Companion to Phase 1A Sections 2–16 | April 4–24, 2026 | All fixes apply on top of the original plan without replacing it*
