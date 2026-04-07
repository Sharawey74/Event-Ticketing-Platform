# AI-Augmented Development Workflow Strategy
## Event Ticketing Platform — Phase 1A

> **Role:** Senior Software Architect + AI-Augmented Development Expert + Technical Workflow Designer  
> **Tools:** Spec Kit (`spec-driven-workflow-v1.instructions.md`) + Awesome Copilot (instructions, skills, plugins)  
> **Project:** Modular Monolith · Java 21 · Spring Boot · PostgreSQL · Redis · RabbitMQ · Stripe · Next.js 14

---

## Table of Contents

1. [Global Workflow Design](#1-global-workflow-design)
2. [Section-by-Section Integration (All 16 Sections)](#2-section-by-section-integration)
3. [Practical Command Flow — Real Examples](#3-practical-command-flow)
4. [Copilot Configuration Strategy](#4-copilot-configuration-strategy)
5. [Workflow Control Strategy](#5-workflow-control-strategy)
6. [Selective Usage Strategy](#6-selective-usage-strategy)
7. [Infrastructure Integration (Sections 6–9)](#7-infrastructure-integration)
8. [Testing & Deployment Integration (Sections 10–12)](#8-testing--deployment-integration)
9. [Engineering Practices Enforcement (Sections 13–16)](#9-engineering-practices-enforcement)
10. [Final Workflow Blueprint](#10-final-workflow-blueprint)

---

## 1. Global Workflow Design

### The Two-Tool Mental Model

```
SPEC KIT — Controls the WHAT and WHEN
  ↓ Defines requirements, design, tasks, validation gates
  ↓ Prevents "vibe coding" — no code until spec is approved

AWESOME COPILOT — Controls the HOW WELL
  ↓ Injects domain expertise into every prompt
  ↓ Enforces Spring Boot, security, testing, and clean code standards
  ↓ Improves AI output quality at the implementation layer
```

### The 6-Phase Spec Kit Lifecycle (Mapped to This Project)

| Phase | Spec Kit Phase | Trigger | Input Required | Output |
|---|---|---|---|---|
| 1 | **ANALYZE** | Start of every new feature or day | Original plan section + Adjustments overlay | `requirements.md` in EARS notation |
| 2 | **DESIGN** | After requirements are locked | `requirements.md` + architecture docs (Sections 3–5) | `design.md` with sequence diagrams, data models |
| 3 | **IMPLEMENT** | After design is approved | `design.md` + `tasks.md` | Production Java/Next.js code |
| 4 | **VALIDATE** | After each task group | Tests + JaCoCo + k6 results | Test reports, coverage delta |
| 5 | **REFLECT** | End of each day (Day 7/14/21 deep) | All code and tests for the day | Refactoring notes, tech debt log |
| 6 | **HANDOFF** | Before moving to next day/section | Session handoff summary | PR description, `tasks.md` updated |

### Where Awesome Copilot Is Injected

```
BEFORE every prompt:
  → .github/copilot-instructions.md is always active (project context)
  → Activate relevant instruction file for the domain (e.g., springboot.instructions.md)

DURING ANALYZE phase:
  → context-engineering.instructions.md — structures the problem context
  → oop-design-patterns.instructions.md — identifies patterns during design

DURING DESIGN phase:
  → object-calisthenics.instructions.md — enforces clean OOP rules
  → containerization-docker-best-practices.instructions.md — when designing Docker layer

DURING IMPLEMENT phase:
  → springboot.instructions.md — active on all *.java files
  → security-and-owasp.instructions.md — active on auth, payment, webhook code
  → performance-optimization.instructions.md — active on Redis, locking code

DURING VALIDATE phase:
  → code-review-generic.instructions.md — run review before marking done
  → self-explanatory-code-commenting.instructions.md — ensure intent comments exist

DURING HANDOFF phase:
  → update-docs-on-code-change.instructions.md — keeps docs in sync
```

---

## 2. Section-by-Section Integration

---

### Section 2 — Day-by-Day Execution Map

**A) Spec Kit Usage**

This section is your **master task board**. Before Day 1 begins:

```
Action: Create tasks.md from the Section 2 day-by-day table.
Format: Each day becomes a Phase header. Each task within the day becomes a [ ] checkbox.

Example tasks.md structure:
  ## Phase 1 — Week 1 (Days 1–7)
  ### Day 1 — Project Init + DB Schema
  - [ ] Initialize Spring Boot project structure
  - [ ] Write Flyway V1__create_users_table.sql
  - [ ] Write Flyway V2–V7 (events, tiers, bookings, payments)
  - [ ] Create JPA entities (User, Event, Booking, Ticket, Payment)
  - [ ] Apply Fix 1.1 (Instant), Fix 1.2 (PG ENUM), Fix 1.3 (soft delete)
  - [ ] Run: ./mvnw clean verify → confirm build passes
```

**B) Awesome Copilot Usage**

- **Resource type:** `task-implementation.instructions.md`
- **Why:** This instruction enforces a mandatory plan-read-before-code pattern. It prevents jumping to implementation without reading the task details first.
- **Activation:** Apply this instruction globally from Day 1 so every implementation session follows the same discipline.

**C) Developer Actions**

1. Open `tasks.md` — read the entire day's checklist before touching any code
2. Activate `springboot.instructions.md` in your Copilot session
3. For each task: implement → validate → mark `[x]` → append to session handoff

**D) Expected Output**

- `tasks.md` — living checklist, updated after every task completion
- Session handoff summary at end of each day

---

### Sections 3–5 — Architecture, ERD, API Definitions

**A) Spec Kit Usage**

These sections are a pre-written `design.md`. Formalize them:

```
Action: Create .specify/design.md

Contents to transfer from Section 3:
  - Backend package structure diagram
  - JPA entity relationships (ERD)
  - Booking flow sequence diagram
  - API endpoint table

Contents to transfer from Section 4:
  - Next.js component hierarchy
  - State management architecture (Zustand, React Query)
  - Route structure

Contents to transfer from Section 5:
  - Docker Compose service topology
  - RabbitMQ exchange/queue topology
```

The Spec Kit DESIGN phase requires this artifact to exist before any code is written. Section 3–5 documents are your design artifact.

**B) Awesome Copilot Usage**

- **Resource type:** `oop-design-patterns.instructions.md` — Reference when validating the domain package structure against OOP principles
- **Resource type:** `context-engineering.instructions.md` — Use when building the `design.md` context to ensure the AI understands the full system before generating any entity or repository
- **Why:** Without this context, Copilot generates generic JPA entities. With it, it generates entities that match the specific ERD relationships (e.g., `@ManyToOne` between `Booking` and `TicketTier`, not just a `Long tierId` field).

**C) Developer Actions**

1. Create `.specify/design.md` by transferring ERD + sequence diagrams from the PDFs
2. Create `.specify/requirements.md` using EARS notation for the core booking flow:
   ```
   WHEN a user selects a ticket tier and quantity,
   THE SYSTEM SHALL acquire a Redis distributed lock
   scoped to tier:{tierId}:user:{userId} and decrement
   the available seat count atomically.
   ```
3. Activate `oop-design-patterns.instructions.md` when asking Copilot to review entity design

**D) Expected Output**

- `.specify/requirements.md` — 15–20 EARS-formatted requirements covering the full booking lifecycle
- `.specify/design.md` — Architecture blueprint (ERD, sequences, package structure, API contracts)

---

### Section 6 — Redis Implementation

**A) Spec Kit Usage**

Before writing `DistributedLockService.java`:

```
Spec Kit ANALYZE phase:
  - Define requirements in EARS notation:
    WHEN a user initiates a seat reservation,
    THE SYSTEM SHALL atomically acquire a Redis lock
    using SET NX EX with a UUID value scoped to
    tier:{tierId}:user:{userId}.

    IF the lock key already exists (NX fails),
    THEN THE SYSTEM SHALL throw LockAcquisitionException
    and reject the reservation request.

    WHEN releasing the lock,
    THE SYSTEM SHALL execute a Lua script that verifies
    the stored UUID matches the caller's UUID before
    deleting the key.

Spec Kit DESIGN phase:
  - Map to design.md: Add DistributedLockService to the
    common/util package. Document: acquireLock(), releaseLock(),
    executeWithLock() method signatures.
  - Confidence Score: 95% — proceed to full implementation.

Spec Kit TASKS phase:
  - [ ] Write RESERVE_SEAT_LUA constant (Lua floor guard)
  - [ ] Write RELEASE_LOCK_LUA constant (ownership check)
  - [ ] Implement acquireLock() using RedisTemplate.execute()
  - [ ] Implement releaseLock() using Lua script
  - [ ] Implement executeWithLock() with try-finally
  - [ ] Write DistributedLockServiceTest (unit — Mockito)
  - [ ] Write InventoryServiceIntegrationTest (Testcontainers Redis)
```

**B) Awesome Copilot Usage**

- **Resource type:** `performance-optimization.instructions.md` — Enforces sub-millisecond operation principles; prevents introducing blocking network calls inside the lock body
- **Resource type:** `security-and-owasp.instructions.md` — Validates that lock values are UUID-based (not predictable), preventing lock hijacking
- **Why:** Without these instructions, Copilot may generate a 2-command SETNX+EXPIRE pattern (the well-known bug the plan explicitly warns against). The instructions enforce the atomic `SET NX EX` single-command pattern.

**C) Developer Actions**

1. Open session with `spec-driven-workflow-v1.instructions.md` + `springboot.instructions.md` active
2. Prime Copilot with the EARS requirements before asking for code
3. Generate `DistributedLockService.java` — Copilot will apply Redis best practices from `performance-optimization.instructions.md`
4. Generate the Lua scripts as static constants in the class
5. Run `InventoryServiceTest` with Testcontainers Redis — verify floor guard works

**D) Expected Output**

- `com.ticketing.common.util.DistributedLockService.java`
- `com.ticketing.common.util.BusinessConstants.java` (Fix CC-2 applied here)
- Two Lua script string constants (RESERVE_SEAT_LUA, RELEASE_LOCK_LUA)
- `InventoryServiceTest.java` with concurrent 100-thread test

---

### Section 7 — RabbitMQ Configuration

**A) Spec Kit Usage**

```
Spec Kit ANALYZE phase:
  EARS requirements:
    WHEN a booking is confirmed via Stripe webhook,
    THE SYSTEM SHALL publish a BookingConfirmedEvent
    to booking.exchange with routing key booking.confirmed.

    WHEN a message consumer fails to process a message,
    THE SYSTEM SHALL nack the message without requeue,
    routing it to the dead letter queue.

Spec Kit DESIGN phase:
  Add to design.md:
    - Exchange topology: booking.exchange (Topic), notification.exchange (Direct)
    - Queue table with DLQ pairing and x-message-ttl values
    - Message payload schemas (BookingConfirmedEvent, RefundDeniedEvent fields)
```

**B) Awesome Copilot Usage**

- **Resource type:** `springboot.instructions.md` — Enforces `@RabbitListener` with `@RequiredArgsConstructor`, `Jackson2JsonMessageConverter` usage, and `channel.basicAck()` in finally blocks
- **Why:** Copilot without context generates auto-ACK listeners. The instruction enforces manual ACK which is non-negotiable for at-least-once delivery.

**C) Developer Actions**

1. Define the full exchange/queue topology in `design.md` before generating any config
2. Generate `RabbitMQConfig.java` — reference design.md in the prompt context
3. Validate: run Docker Compose, open RabbitMQ Management UI (localhost:15672), verify all exchanges and queues appear with correct bindings

**D) Expected Output**

- `com.ticketing.common.config.RabbitMQConfig.java`
- All queue/exchange/binding `@Bean` declarations
- `BookingNotificationListener.java` with manual ACK

---

### Section 8 — Spring State Machine

**A) Spec Kit Usage**

The state machine is the most complex feature. Spec Kit's ANALYZE phase is mandatory here.

```
Spec Kit ANALYZE phase:
  Produce a full transition table as a requirement artifact:

  | From State       | Event              | To State       | Guard              | Action                    |
  |------------------|--------------------|----------------|---------------------|---------------------------|
  | AVAILABLE        | ADD_TO_CART        | RESERVED       | SeatsAvailableGuard | StartReservationTimerAction|
  | RESERVED         | PROCEED_TO_CHECKOUT| PAYMENT_PENDING| —                   | —                         |
  | RESERVED         | TIMER_EXPIRED      | EXPIRED        | —                   | ReleaseSeatsAction        |
  | PAYMENT_PENDING  | PAYMENT_SUCCESS    | CONFIRMED      | —                   | ConfirmBookingAction      |
  | ...              | ...                | ...            | ...                 | ...                       |
  | CONFIRMED        | CHECK_IN           | ATTENDED       | CheckInGuard        | —                         |

  Add Fix 11.1: CANCELLED state transitions from CONFIRMED, RESERVED, PAYMENT_PENDING.
  Add Fix 8.2: CheckInGuard with ORGANIZER role check.

Spec Kit DESIGN phase:
  Confidence Score: 75% (Spring State Machine API is complex)
  → Action: Build a PoC with 3 states (AVAILABLE → RESERVED → EXPIRED) first.
  → Validate PoC, then expand to all 11 states.

Spec Kit TASKS phase:
  - [ ] Define BookingState enum (11 values including CANCELLED)
  - [ ] Define BookingEvent enum (10 values including EVENT_CANCELLED)
  - [ ] Write BookingStateMachineConfig (@EnableStateMachineFactory)
  - [ ] Write SeatsAvailableGuard
  - [ ] Write CheckInGuard (Fix 8.2)
  - [ ] Write StartReservationTimerAction
  - [ ] Write ReleaseSeatsAction
  - [ ] Write ConfirmBookingAction
  - [ ] Write GenerateQRCodeAction (publishes to ticket.generation.queue — Fix 10.2)
  - [ ] Write NotifyRefundDeniedAction (Fix 10.1)
  - [ ] Write CancelBookingAction (Fix 11.1)
  - [ ] Write BookingStateMachineService (stateless-per-request wrapper)
  - [ ] Write BookingStateMachineTest (5 required test cases)
```

**B) Awesome Copilot Usage**

- **Resource type:** `oop-design-patterns.instructions.md` — The State pattern is directly applicable; Copilot will structure Guards and Actions as proper OOP collaborators, not inline lambdas
- **Resource type:** `springboot.instructions.md` — Prevents `@EnableStateMachine` (singleton bug) and enforces `@EnableStateMachineFactory`
- **Why:** The difference between `@EnableStateMachine` and `@EnableStateMachineFactory` is the single most critical Spring State Machine decision. The instructions enforce this before any code is generated.

**C) Developer Actions**

1. Create the transition table as a markdown table in `design.md` — this IS the spec
2. Activate `spec-driven-workflow-v1.instructions.md` + `oop-design-patterns.instructions.md`
3. Generate Guards and Actions as separate `@Component` classes — never inline
4. Build PoC (3-state machine), run `BookingStateMachineTest`, confirm green
5. Expand to full 11-state machine

**D) Expected Output**

- `com.ticketing.booking.statemachine.BookingStateMachineConfig.java`
- 3 Guard classes, 5 Action classes in `com.ticketing.booking.statemachine`
- `BookingStateMachineService.java` with stateless-per-request wrapper
- `BookingStateMachineTest.java` with all 5 required test cases green

---

### Section 9 — Stripe Payment Integration

**A) Spec Kit Usage**

```
Spec Kit ANALYZE phase:
  EARS requirements:
    WHEN a user initiates checkout,
    THE SYSTEM SHALL create a Stripe Checkout Session
    and transition the booking to PAYMENT_PENDING state
    before the session URL is returned.

    WHEN Stripe delivers a checkout.session.completed event,
    THE SYSTEM SHALL verify the webhook signature using
    Webhook.constructEvent(), attempt to insert the stripe_event_id
    into processed_stripe_events, and handle
    DataIntegrityViolationException as silent idempotent success.

    WHEN the webhook DB commit is successful,
    THE SYSTEM SHALL return HTTP 200. On any other exception,
    THE SYSTEM SHALL return HTTP 500 to trigger Stripe retry.

Spec Kit TASKS phase:
  - [ ] Implement PaymentService.createCheckoutSession()
  - [ ] Implement PaymentService.handleStripeWebhook() (@Transactional)
  - [ ] Implement ProcessedStripeEvent entity + repository
  - [ ] Create V6__create_payments_and_refunds.sql (UNIQUE on stripe_event_id)
  - [ ] Implement WebhookController (no @Transactional — Fix 9.1)
  - [ ] Configure Stripe CLI local webhook listener
  - [ ] Write PaymentServiceTest (unit — mock Stripe SDK)
  - [ ] Write PaymentWebhookTest (invalid sig → 400, duplicate → 200, success → CONFIRMED)
```

**B) Awesome Copilot Usage**

- **Resource type:** `security-and-owasp.instructions.md` — Enforces HMAC-SHA256 signature verification before any payload parsing; prevents processing unsigned webhooks
- **Why:** A Copilot session without this instruction may generate a working webhook handler that skips signature verification. The OWASP instruction explicitly addresses integrity failure (A08) which is exactly what the raw-payload signature check prevents.

**C) Developer Actions**

1. Run: `stripe login` → `stripe listen --forward-to localhost:8080/api/webhooks/stripe`
2. Generate `PaymentService.java` with `security-and-owasp.instructions.md` active
3. Test locally: `stripe trigger checkout.session.completed`
4. Verify: booking transitions to CONFIRMED, QR generation message appears in `ticket.generation.queue`, email appears in MailHog

**D) Expected Output**

- `com.ticketing.payment.service.PaymentService.java`
- `com.ticketing.payment.controller.WebhookController.java`
- `com.ticketing.payment.model.ProcessedStripeEvent.java`
- `PaymentWebhookTest.java` covering all 3 scenarios

---

### Section 10 — RabbitMQ Consumers + QR Generation

**A) Spec Kit Usage**

```
Spec Kit TASKS:
  - [ ] Write BookingNotificationListener (manual ACK, MDC correlation - Fix CC-1)
  - [ ] Write QRCodeGenerationListener (consumes ticket.generation.queue - Fix 10.2)
  - [ ] Write NotifyRefundDeniedListener (Fix 10.1)
  - [ ] Write CorrelationIdFilter (Fix CC-1)
  - [ ] Write NotificationListenerIntegrationTest (Awaitility, not Thread.sleep)
```

**B) Awesome Copilot Usage**

- **Resource type:** `performance-optimization.instructions.md` — Enforces async consumer patterns, prevents DB calls in the hot path of message consumers
- **Why:** Fix 10.2 requires understanding that synchronous QR generation in the webhook is a latency violation. The performance instruction makes Copilot aware of async offloading principles.

---

### Section 11 — State Machine Full Config

**A) Spec Kit Usage**

This is a REFLECT phase after Section 8's implementation:

```
Spec Kit REFLECT tasks:
  - [ ] Verify CANCELLED state is wired (Fix 11.1)
  - [ ] Document RELEASE event caller explicitly (Fix 11.2)
  - [ ] Run all 5 required state machine tests → confirm green
  - [ ] Update design.md transition table with CANCELLED paths
```

---

### Section 12 — Pricing Engine + Refund Logic + Waitlist

**A) Spec Kit Usage**

```
Spec Kit ANALYZE phase:
  PricingEngine has Confidence Score: 100%
  (Pure function, no I/O, deterministic math)
  → Skip PoC, proceed directly to full implementation with 100% branch coverage.

  EARS requirements:
    WHEN available seats exceed 80% of tier capacity,
    THE SYSTEM SHALL apply a 1.25× surge multiplier.

    WHEN days until event >= 30,
    THE SYSTEM SHALL apply a 0.50× early bird multiplier.

    WHEN quantity >= 5,
    THE SYSTEM SHALL apply a 0.10 discount.

    (All multipliers use BusinessConstants — never magic numbers)

Spec Kit TASKS:
  - [ ] Write PricingEngine with 4 pricing methods
  - [ ] Write PricingEngineTest — 100% branch coverage
  - [ ] Test boundary: exactly 80% sold (no surge) vs 81% (surge)
  - [ ] Write RefundService with 3-tier refund logic
  - [ ] Add refund_denial_reason column (Fix 12.1)
  - [ ] Write WaitlistService (Redis Sorted Set — ZPOPMIN)
```

**B) Awesome Copilot Usage**

- **Resource type:** `object-calisthenics.instructions.md` — Enforces no primitives in pricing method signatures (wraps quantity in a `Quantity` value object, wraps percentage in a `BigDecimal` with clear naming)
- **Why:** Pricing bugs often stem from using `double` instead of `BigDecimal`, or passing arguments in the wrong order (`price, quantity` vs `quantity, price`). Object Calisthenics prevents both.

---

### Sections 13–16 — Practices, Resources, Troubleshooting, Transition

These sections map to the REFLECT and HANDOFF phases of Spec Kit. Detailed in [Section 9](#9-engineering-practices-enforcement).

---

## 3. Practical Command Flow

### Auth System — Day 2

**Step 1: ANALYZE**
```
Prompt to Copilot (with spec-driven-workflow-v1.instructions.md active):
"I am in the ANALYZE phase for the JWT authentication system.
Create requirements.md entries in EARS notation for:
1. User registration
2. User login returning a JWT
3. Token validation on protected endpoints
4. Role-based access control (USER, ORGANIZER, ADMIN)"
```

**Step 2: DESIGN**
```
Prompt (with springboot.instructions.md + security-and-owasp.instructions.md active):
"Based on these EARS requirements, create the design.md section for the auth system.
Include: JwtService method signatures, SecurityFilterChain configuration,
JwtAuthenticationFilter flow, and UserDetailsService implementation.
Package: com.ticketing.common.security"
```

**Step 3: TASKS**
```
Append to tasks.md:
### Day 2 — Auth System
- [ ] Create User entity with Role @Enumerated(EnumType.STRING) (Fix 1.2)
- [ ] Write UserRepository + UserDetailsService
- [ ] Write JwtService (generateToken, validateToken, extractUsername)
- [ ] Write JwtAuthenticationFilter extends OncePerRequestFilter
- [ ] Write SecurityConfig (filter chain, CSRF exclusions for /webhooks/stripe)
- [ ] Write AuthController (POST /api/auth/register, POST /api/auth/login)
- [ ] Write AuthControllerTest (@WebMvcTest)
- [ ] Write UserServiceTest (Mockito)
```

**Step 4: IMPLEMENT**
```
Prompt (with springboot.instructions.md + security-and-owasp.instructions.md active):
"Implement JwtService.java in com.ticketing.common.security.
Requirements:
- Constructor injection via @RequiredArgsConstructor
- Read JWT_SECRET from @ConfigurationProperties, never hardcoded
- Use io.jsonwebtoken 0.11.x API (Jwts.parserBuilder)
- Token expiry: read from config property (jwt.expiry-ms)
- Methods: generateToken(UserDetails), validateToken(String, UserDetails), extractUsername(String)
Apply all OWASP A02 (cryptographic) recommendations from the active instruction."
```

---

### Ticket Booking Flow — Day 8

**ANALYZE prompt:**
```
"ANALYZE phase for BookingService.reserveTickets().
Using the TOCTOU fix (Fix 8.1) from Phase1A_Adjustments_and_Fixes.md,
produce EARS requirements that capture:
1. Pre-lock availability check (fast path)
2. Lock acquisition (Redis SET NX EX)
3. Post-lock availability re-check (TOCTOU guard)
4. Atomic seat reservation (Lua floor guard from Fix 5.1)
5. Booking entity creation (state=RESERVED, expiresAt=Instant.now()+300s)
6. Lock release in finally block"
```

**TASKS prompt:**
```
"Create tasks.md entries for Day 8 BookingService implementation.
Each task must reference the specific class and method to implement.
Each task must include its test counterpart as a sub-task."
```

**IMPLEMENT prompt:**
```
"Implement BookingService.reserveTickets() in com.ticketing.booking.service.
Apply:
- @Transactional on the method
- @RequiredArgsConstructor (no @Autowired)
- All time fields use Instant (never LocalDateTime)
- Double-check inside lock (Fix 8.1)
- Lua floor guard via inventoryService.reserveSeat() (Fix 5.1)
- BusinessConstants.RESERVATION_TTL_SECONDS (no magic numbers — Fix CC-2)
- CorrelationId from MDC in all log statements (Fix CC-1)"
```

---

### Stripe Payment — Day 9

**IMPLEMENT prompt:**
```
"Implement PaymentService.handleStripeWebhook() in com.ticketing.payment.service.
Apply:
- @Transactional (Fix 9.1 — return 200 only after DB commit)
- Raw payload String parameter for signature verification (NOT parsed body)
- Webhook.constructEvent(payload, sigHeader, webhookSecret) — OWASP A08 compliance
- Insert-first idempotency pattern with DataIntegrityViolationException catch (Fix 9.2)
- Switch on stripeEvent.getType() with checkout.session.completed and checkout.session.expired
- BookingStateMachineService.sendEvent() for state transition
Active instructions: springboot.instructions.md + security-and-owasp.instructions.md"
```

**VALIDATE prompt:**
```
"Review WebhookController.java against:
1. spec-driven-workflow-v1 VALIDATE phase checklist
2. security-and-owasp.instructions.md A08 integrity rules
3. Verify: does the controller have @Transactional? (it MUST NOT)
4. Verify: does it return 500 on unexpected exceptions? (it MUST)
5. Verify: does it return 400 ONLY on SignatureVerificationException? (it MUST)"
```

---

### Event Management — Days 2–4

**IMPLEMENT prompt:**
```
"Implement EventService.java in com.ticketing.event.service.
Apply:
- Class-level @Transactional(readOnly = true) — Fix 2.1
- @Transactional (write) on createEvent(), updateEvent(), publishEvent()
- @RequiredArgsConstructor — Fix 2.2
- @EntityGraph on findById() to prevent N+1
- @Cacheable('event:{id}') on getEventById(), @CacheEvict on mutations
- ApiResponse<T> wrapper on all controller return types
Active instructions: springboot.instructions.md"
```

---

## 4. Copilot Configuration Strategy

### `.github/copilot-instructions.md` — Master Configuration

Create this file in the project root. This file is ALWAYS active for every Copilot session in this repo.

```markdown
# GitHub Copilot Instructions — Event Ticketing Platform

## Project Context
This is a production-grade Event Ticketing Platform (Phase 1A).
Architecture: Modular Monolith · Spring Boot 3 · Java 21 · PostgreSQL 15
Redis 7 · RabbitMQ 3.12 · Stripe API · Next.js 14 (TypeScript)

## Non-Negotiable Rules (Apply to Every File)

### Dependency Injection
- ALWAYS use constructor injection via @RequiredArgsConstructor
- NEVER use @Autowired field injection
- ALL fields must be private final

### Time Handling
- ALWAYS use Instant for point-in-time fields
- NEVER use LocalDateTime for fields that represent a moment in time
- Database columns use TIMESTAMPTZ, Java uses Instant

### Transactions
- @Transactional(readOnly = true) at class level for all @Service classes
- Override with @Transactional (write) on mutating methods ONLY
- @Transactional NEVER on @RestController classes

### Naming & Constants
- NEVER use magic numbers — ALL business rule values are in BusinessConstants.java
- Package: com.ticketing.{domain}.{layer}
- Valid domains: event, booking, payment, inventory, notification, pricing, user
- Shared: com.ticketing.common.{config|filter|exception|util|security}

### Error Handling
- ALL exceptions include context: "Booking not found: id=" + id
- GlobalExceptionHandler handles all domain exceptions
- HTTP status codes: 404 NotFound, 409 Conflict (state machine rejection), 400 Bad Request, 500 retry-eligible

### Logging
- Use @Slf4j (Lombok) on every class
- NEVER System.out.println()
- Log format: log.info("Action [bookingId={}]: {}", bookingId, message)
- Include MDC correlationId in all service-layer log statements

### Testing
- Unit tests: JUnit 5 + Mockito + AssertJ (NEVER assertEquals — use assertThat)
- Integration tests: Testcontainers with BaseIntegrationTest shared containers
- @DisplayName on every test method (human-readable sentence)
- Async assertions: Awaitility — NEVER Thread.sleep()

## Critical Components (Extra Attention Required)
- DistributedLockService: Lua scripts, SET NX EX, UUID ownership, try-finally release
- BookingStateMachineService: @EnableStateMachineFactory, stateless-per-request, sm.stop() in finally
- PaymentService.handleStripeWebhook(): raw payload, signature verify, insert-first idempotency
- InventoryService.reserveSeat(): Lua floor guard, never plain DECR

## Stack Reference
- Spring Boot: @ConfigurationProperties for type-safe config binding
- Redis: RedisTemplate<String, String>, Lua scripts via DefaultRedisScript<Long>
- RabbitMQ: Jackson2JsonMessageConverter, manual ACK (channel.basicAck/basicNack)
- Stripe: stripe-java 23.x, Webhook.constructEvent()
- Flyway: V{N}__{description}.sql, TIMESTAMPTZ not TIMESTAMP
```

---

### Instruction Files by Task Type

| Task Area | Instruction File to Activate | When to Activate |
|---|---|---|
| All Java code | `springboot.instructions.md` | Every Java session |
| Auth, webhooks, Stripe | `security-and-owasp.instructions.md` | Days 2, 9 |
| Redis, concurrent code | `performance-optimization.instructions.md` | Days 5, 8, 14 |
| State machine, domain design | `oop-design-patterns.instructions.md` | Days 8, 11 |
| Docker Compose, Dockerfile | `containerization-docker-best-practices.instructions.md` | Days 5, 18 |
| CI/CD pipelines | `github-actions-ci-cd-best-practices.instructions.md` | Day 18 |
| Code review before PR | `code-review-generic.instructions.md` | Days 7, 14, 21 |
| Comments and docs | `self-explanatory-code-commenting.instructions.md` | Day 22 |
| Clean code + OOP rules | `object-calisthenics.instructions.md` | Days 12, 19 |

### Skills to Use

| Skill | Directory | When to Use |
|---|---|---|
| `java-springboot` | `skills/java-springboot/SKILL.md` | Every backend implementation session |

**How to activate a skill:**
```
In Copilot chat: @workspace /skill java-springboot
Or: Reference the SKILL.md content in your session context
```

---

## 5. Workflow Control Strategy

### The Gate System — How to Prevent Vibe Coding

Every code generation is blocked behind a gate. You do NOT open VS Code and start typing until each gate is passed.

```
GATE 1 — ANALYZE COMPLETE
  ✅ requirements.md entry written in EARS notation
  ✅ All acceptance criteria are testable (you can name the test before writing it)
  ✅ All dependencies identified
  🚫 BLOCKED: Do not open any .java file until this gate passes

GATE 2 — DESIGN COMPLETE
  ✅ design.md section updated (method signatures, data models)
  ✅ Confidence Score assigned (>85% = full impl, 66-85% = PoC first)
  ✅ tasks.md entries created for the feature
  🚫 BLOCKED: Do not generate any implementation code until this gate passes

GATE 3 — TASK CHECKLIST READY
  ✅ tasks.md has atomic, testable tasks with clear acceptance criteria
  ✅ Each task has a matching test task as a sub-item
  🚫 BLOCKED: Do not start the first task until all tasks are defined

GATE 4 — VALIDATE COMPLETE
  ✅ ./mvnw test passes
  ✅ JaCoCo coverage delta meets target for the day
  ✅ Docker Compose services all show (healthy)
  🚫 BLOCKED: Do not mark a day complete until this gate passes

GATE 5 — HANDOFF COMPLETE
  ✅ Session handoff summary printed
  ✅ tasks.md updated (no [ ] remaining for the day)
  ✅ Session handoff includes "Next session start" pinpointing exact task
```

### When NOT to Jump to Implementation

| Situation | Correct Action |
|---|---|
| You know exactly what the code should look like | Still write the EARS requirement first — it takes 5 minutes and prevents scope creep |
| Copilot suggests code during a planning prompt | Paste it to a scratch file, finish the ANALYZE/DESIGN phase, then implement properly |
| A bug is blocking you | Switch to Spec Kit TROUBLESHOOT mode: re-ANALYZE why the bug exists before fixing it |
| You discover a fix is needed from the Adjustments overlay | Pause implementation, note the fix ID in tasks.md, apply it immediately if 🔴 CRITICAL |
| You're in Day 8 and realize the state machine config is wrong | Stop. REFLECT phase. Update design.md transition table. Re-run DESIGN before changing code. |

### How to Validate Each Phase Before Moving Forward

```
After ANALYZE:
  → Can you name all the acceptance criteria as test method names? YES → proceed

After DESIGN:
  → Is design.md updated? Does the sequence diagram show the new flow? YES → proceed

After IMPLEMENT:
  → Does ./mvnw clean verify pass? YES → proceed
  → Is every new class in the correct package? YES → proceed

After VALIDATE:
  → Is JaCoCo delta positive? YES → proceed
  → Did the ConcurrentBookingTest pass? YES → proceed (for Days 8, 14)

After REFLECT:
  → Are all @TODO and FIXME comments resolved? YES → proceed
  → Is Javadoc present on all public methods? YES → proceed
```

---

## 6. Selective Usage Strategy

### ALWAYS Use Spec Kit For:

```
✅ Every new feature (no exceptions)
✅ Every architectural decision (e.g., "should QR gen be sync or async?")
✅ Every database schema change
✅ Every API endpoint addition
✅ Every state machine modification (new state, new event, new guard)
✅ Week-end cleanup (Days 7, 14, 21)
✅ When you're unsure what to build next
```

### NEVER Use Spec Kit For:

```
❌ Single-line bug fixes (e.g., typo in a string constant)
❌ Adding a @DisplayName annotation to an existing test
❌ Renaming a variable for clarity
❌ Adjusting an application.yml property value
```

### ALWAYS Use Awesome Copilot Instructions For:

```
✅ Any time you open a new Copilot session (default instruction stack)
✅ When generating code in a new domain (activate the domain-specific instruction)
✅ Before any security-sensitive code (auth, webhooks, Stripe)
✅ Before writing CI/CD YAML
✅ Before a code review session
```

### USE Awesome Copilot Instructions Selectively For:

```
Use security-and-owasp.instructions.md ONLY when:
  → Writing auth filters, webhook handlers, payment services
  (Not needed for PricingEngine, EventService CRUD)

Use performance-optimization.instructions.md ONLY when:
  → Writing Redis, lock services, inventory service
  (Not needed for simple repository queries)

Use oop-design-patterns.instructions.md ONLY when:
  → Designing Guards, Actions, Strategies (domain modeling sessions)
  (Not needed for utility classes, DTOs)

Use github-actions-ci-cd-best-practices.instructions.md ONLY when:
  → Creating or modifying .github/workflows/*.yml
  (Not relevant during daily backend implementation)
```

### Decision Matrix

```
New Feature → Spec Kit ANALYZE → DESIGN → TASKS → then Copilot
Bug Fix → Copilot directly (if trivial) → Spec Kit ANALYZE (if architectural)
Performance → Spec Kit ANALYZE + performance-optimization.instructions.md
Security → Spec Kit ANALYZE + security-and-owasp.instructions.md
Code Review → code-review-generic.instructions.md (Copilot only)
Documentation → update-docs-on-code-change.instructions.md (Copilot only)
```

---

## 7. Infrastructure Integration (Sections 6–9)

### Redis — Spec Kit + Copilot Integration

| Concern | Spec Kit Role | Copilot Role |
|---|---|---|
| Distributed lock design | DESIGN phase: method signatures, Lua scripts, TTL decisions documented in design.md | `performance-optimization.instructions.md` enforces atomic operations, prevents blocking patterns |
| Inventory floor guard | ANALYZE: EARS requirement "IF decrement would cause negative count, THEN return -1" | `springboot.instructions.md` enforces `DefaultRedisScript<Long>` pattern |
| Cache-aside pattern | DESIGN: document cache keys, TTL values, eviction rules in design.md | `springboot.instructions.md` provides correct `@Cacheable`/`@CacheEvict` usage |
| Warmup on startup | TASKS: Fix 5.2 as explicit task item | `performance-optimization.instructions.md` enforces HealthIndicator readiness gate |

### RabbitMQ — Spec Kit + Copilot Integration

| Concern | Spec Kit Role | Copilot Role |
|---|---|---|
| Exchange topology | DESIGN: formal topology diagram in design.md before any config code | `springboot.instructions.md` enforces durable, `Jackson2JsonMessageConverter` |
| Manual acknowledgement | ANALYZE: EARS "WHEN consumer processes message, THE SYSTEM SHALL call basicAck after success" | `springboot.instructions.md` prevents auto-ACK default |
| DLQ routing | DESIGN: DLQ pairing as explicit design decision | Inline with `springboot.instructions.md` |
| Retry strategy | TASKS: Fix for retry exchange as explicit task | `performance-optimization.instructions.md` suggests exponential backoff |

### Stripe — Spec Kit + Copilot Integration

| Concern | Spec Kit Role | Copilot Role |
|---|---|---|
| Checkout session creation | DESIGN: full session payload schema in design.md | `security-and-owasp.instructions.md` enforces secrets from env vars |
| Webhook signature verify | ANALYZE: EARS "IF signature invalid THEN return 400" | `security-and-owasp.instructions.md` A08 integrity check enforcement |
| Idempotency | DESIGN: `ProcessedStripeEvent` table as explicit design artifact | `springboot.instructions.md` enforces `DataIntegrityViolationException` catch |
| 200 after commit | TASKS: Fix 9.1 as critical task item | `springboot.instructions.md` `@Transactional` on service, not controller |

### Spring State Machine — Spec Kit + Copilot Integration

| Concern | Spec Kit Role | Copilot Role |
|---|---|---|
| State/event enum definition | DESIGN: complete transition table as markdown in design.md | `oop-design-patterns.instructions.md` validates State pattern correctness |
| Factory vs singleton | ANALYZE: required behavior "thread-safe per-request" → forces `@EnableStateMachineFactory` | `springboot.instructions.md` prevents singleton `@EnableStateMachine` |
| Guard/Action as components | DESIGN: each guard and action as separate named class in design.md | `oop-design-patterns.instructions.md` enforces Single Responsibility |
| CANCELLED state | TASKS: Fix 11.1 as explicit TASKS entry | `springboot.instructions.md` patterns apply |

---

## 8. Testing & Deployment Integration (Sections 10–12)

### How Spec Kit Defines Testing Tasks

Every TASKS entry must have a paired test task:

```markdown
### Day 5 — InventoryService
- [ ] Implement InventoryService.reserveSeat() (Lua floor guard)
  - [ ] InventoryServiceTest: reserveSeat_whenCountIsZero_shouldReturnFalse
  - [ ] InventoryServiceTest: reserveSeat_concurrent100Threads_exactly50Succeed (Fix 16.1)
  - [ ] InventoryServiceTest: reserveSeat_whenKeyMissing_shouldReturnFalse
- [ ] Implement InventoryService.releaseSeat()
  - [ ] InventoryServiceTest: releaseSeat_shouldIncrementCount
```

This pattern ensures TDD — the test task is listed before the implementation task so you write the test first.

### Coverage Strategy Per Spec Kit VALIDATE Phase

```
VALIDATE checklist (add to tasks.md Day 16):
  - [ ] Run: ./mvnw verify -Pjacoco
  - [ ] Open: target/site/jacoco/index.html
  - [ ] Identify ALL red/yellow lines (< 80% coverage)
  - [ ] For each uncovered branch: create a [ ] test task
  - [ ] PricingEngine: must show 100% green
  - [ ] BookingStateMachine: must show 100% green on guard + action classes
  - [ ] Run again: confirm 80%+ before closing Day 16
```

### How Copilot Improves Test Generation

**Unit Test Generation Prompt:**
```
"Generate unit tests for PricingEngine.calculatePrice() using JUnit 5 + Mockito + AssertJ.
Rules:
- @DisplayName on every test with human-readable sentence
- Use assertThat() from AssertJ — never assertEquals()
- Use BigDecimal assertions: assertThat(result).isEqualByComparingTo("125.00")
- Test boundary conditions: exactly 80% sold (no surge) vs 81% (surge)
- Test all combinations of early bird + group + surge
Active instruction: springboot.instructions.md"
```

**Integration Test Generation Prompt:**
```
"Generate ConcurrentBookingTest.java extending BaseIntegrationTest.
Rules:
- 100 threads competing for 50 seats
- Use CountDownLatch + ExecutorService + AtomicInteger
- Assert: exactly 50 succeed, 50 fail
- Assert: final Redis count is 0
- Assert: final DB booking count for RESERVED state is exactly 50
- No Thread.sleep() — use CountDownLatch.await()
Active instruction: springboot.instructions.md"
```

### CI/CD Script Improvement with Copilot

**CI/CD Generation Prompt:**
```
"Generate .github/workflows/ci.yml for this Spring Boot project.
Requirements (apply github-actions-ci-cd-best-practices.instructions.md):
- Trigger: push to main/develop + PR to main
- Job 1 (test): services: postgres:15, redis:7, rabbitmq:3.12-management
  - Run: ./mvnw verify -Dspring.profiles.active=test
  - Upload JaCoCo report as artifact (retention: 7 days)
  - FAIL build if JaCoCo < 80%
- Job 2 (build): needs: test
  - docker build -t ticketing-platform:${{ github.sha }}
  - Cache: hashFiles('**/pom.xml')
- OIDC for any cloud auth (not static credentials)
- Set permissions: contents: read (minimum privilege)"
```

---

## 9. Engineering Practices Enforcement (Sections 13–16)

### TDD Enforcement — Spec Kit + Copilot Together

```
Spec Kit enforces TDD at the TASKS level:
  → Every implementation task has a sub-task: write the test FIRST
  → The test task is listed ABOVE the implementation task in tasks.md
  → A task is not marked [x] until its tests are green

Copilot enforces TDD at the code level:
  → Prompt template: "Write a failing test for {feature} first.
    Do NOT write the implementation yet. The test must fail with a
    meaningful assertion message that describes what's missing."
  → After the test is written: "Now implement the minimum code
    to make this test pass. Refactor is the next step."
```

### Clean Code Enforcement

Add this enforcement prompt at the START of every implementation session:

```
"Before generating any code, confirm you will apply all 14 clean code rules from
the project's copilot-instructions.md:
1. Constructor injection via @RequiredArgsConstructor
2. No magic numbers — BusinessConstants.java
3. Instant for all time fields
4. @Transactional(readOnly=true) at class level
5. @Slf4j for all logging
6. No System.out.println
7. EARS format for requirements
8. No raw string status values — use enums
9. Exceptions include context
10. @DisplayName on tests
11. AssertJ not JUnit assertEquals
12. Awaitility not Thread.sleep
13. Javadoc on all public methods
14. MDC correlation ID in log statements"
```

### Troubleshooting Workflow

When a bug is encountered, follow Spec Kit's TROUBLESHOOT protocol:

```
Step 1 — RE-ANALYZE:
  "I have a bug: [describe exact symptom + stack trace].
  Analyze this against our requirements.md for BookingService.
  Which EARS requirement is being violated?"

Step 2 — RE-DESIGN:
  "Based on the re-analysis, what is the root cause?
  Update design.md to add an error matrix entry for this failure mode."

Step 3 — FIX:
  "Now implement the fix.
  Apply: security-and-owasp.instructions.md + springboot.instructions.md.
  The fix must not break any existing green tests."

Step 4 — VALIDATE:
  "Reproduce the bug in a test. Confirm the test was red before the fix
  and green after. Add the test to the regression suite."
```

### Phase 1B Transition (Section 16)

**Spec Kit HANDOFF phase checklist for Phase 1A close:**

```markdown
## Phase 1A HANDOFF — Day 21

### Documentation Artifacts
- [ ] README.md — Setup, architecture diagram, tech decision log
- [ ] docs/state-machines.md — Transition table with explanations
- [ ] docs/api.md — Swagger UI link + endpoint table
- [ ] docs/deployment.md — Step-by-step for local + Railway
- [ ] docs/load-testing.md — k6 output, P50/P95/P99, zero oversell verification

### Final Checklist (Adjustments_and_Fixes.md)
- [ ] Run all 17 checklist items from Adjustments_and_Fixes.md Final Checklist
- [ ] Tag: git tag -a v1.0.0 -m "Phase 1A complete"

### Phase 1B Decision
- [ ] Document: what was hardest in Phase 1A?
- [ ] Select: Option A (Social Media Analytics) or Option C (E-Commerce)
- [ ] Write first EARS requirements for the chosen Phase 1B project
```

---

## 10. Final Workflow Blueprint

The complete step-by-step workflow for every development session:

```
╔══════════════════════════════════════════════════════════════════════╗
║                   EVENT TICKETING PLATFORM                           ║
║               AI-AUGMENTED DEVELOPMENT BLUEPRINT                     ║
╚══════════════════════════════════════════════════════════════════════╝

STEP 1 → READ THE PLAN
  Spec Kit role : None yet — human reads Section 2 for current day
  Copilot role  : None yet — no code context needed
  Output        : Understanding of today's scope and fixes to apply

STEP 2 → ANALYZE (Spec Kit Phase 1)
  Spec Kit role : Write EARS requirements into requirements.md
                  Assign Confidence Score (>85% = full, 66-85% = PoC)
                  Identify all fixes from Adjustments_and_Fixes.md for this day
  Copilot role  : context-engineering.instructions.md — structures the prompt context
                  Activate via: "I am in ANALYZE phase for [feature]. Help me write
                  EARS requirements based on this spec: [paste plan section]"
  Output        : requirements.md entries, Confidence Score, fix list

STEP 3 → DESIGN (Spec Kit Phase 2)
  Spec Kit role : Update design.md — method signatures, data model changes,
                  sequence diagram updates, error matrix
  Copilot role  : oop-design-patterns.instructions.md — validates domain model
                  springboot.instructions.md — validates API and service design
  Output        : design.md updated, architectural decisions recorded

STEP 4 → TASKS (Spec Kit TASKS generation)
  Spec Kit role : Append to tasks.md — atomic tasks with test sub-tasks
                  Each 🔴 CRITICAL fix from overlay is a BLOCKING task
  Copilot role  : task-implementation.instructions.md — enforces plan-before-code
  Output        : tasks.md updated with today's unchecked items

STEP 5 → IMPLEMENT (Spec Kit Phase 3)
  Spec Kit role : Implement tasks in dependency order (bottom-up)
                  Update tasks.md [ ] → [/] → [x] in real time
  Copilot role  : springboot.instructions.md (always active)
                + Domain-specific instruction for today's domain
                + security-and-owasp.instructions.md (auth/payment days)
                + performance-optimization.instructions.md (Redis/lock days)
  Output        : Working Java/Next.js code, correct package placement

STEP 6 → VALIDATE (Spec Kit Phase 4)
  Spec Kit role : Run validation checklist —
                  ./mvnw clean verify → BUILD SUCCESS
                  JaCoCo coverage delta → positive
                  Docker Compose → all (healthy)
                  ConcurrentBookingTest → exactly 50 succeed (Days 8+)
  Copilot role  : code-review-generic.instructions.md — review before closing day
                  Prompt: "Review [ClassName].java against our copilot-instructions.md.
                  Report any violations. Do not suggest fixes yet."
  Output        : All tests green, coverage maintained, review notes

STEP 7 → REFLECT (Spec Kit Phase 5)
  Spec Kit role : Refactor if needed, update all docs, log tech debt
                  Deep REFLECT on Days 7, 14, 21 (run full checklist)
  Copilot role  : self-explanatory-code-commenting.instructions.md
                  update-docs-on-code-change.instructions.md
  Output        : Refactored code, updated Javadoc, tech debt log

STEP 8 → HANDOFF (Spec Kit Phase 6)
  Spec Kit role : Print session handoff summary (required by instructions.txt step 6)
  Copilot role  : Generate conventional commit message:
                  Prompt: "Generate a conventional commit message for today's changes.
                  Format: feat(booking): implement distributed lock with Lua floor guard
                  Reference all fix IDs applied: [Fix 5.1, Fix 8.1, Fix CC-2]"
  Output        : Session handoff summary, git commit, tasks.md finalized
```

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────┐
│ DAILY STARTUP COMMAND (paste into Copilot chat)                 │
│                                                                 │
│ "I am starting Day {N} of the Event Ticketing Platform.         │
│  Active documents:                                              │
│  - Phase1A Sections 2-16 (original plan)                        │
│  - Phase1A_Adjustments_and_Fixes.md (overlay)                   │
│  - .specify/requirements.md                                     │
│  - .specify/design.md                                           │
│  - tasks.md                                                     │
│                                                                 │
│  Active instructions:                                           │
│  - spec-driven-workflow-v1.instructions.md                      │
│  - springboot.instructions.md                                   │
│  - [domain-specific instruction for today]                      │
│                                                                 │
│  I am in the ANALYZE phase.                                     │
│  Today's scope: [paste Day N tasks from Section 2]              │
│  Critical fixes to apply: [list fix IDs for today]"            │
└─────────────────────────────────────────────────────────────────┘
```

---

*Strategy complete — covers all 16 sections, all 10 required dimensions*  
*Spec Kit + Awesome Copilot resources are mapped to real files from the workspace*  
*Ready for Day 1 implementation*
