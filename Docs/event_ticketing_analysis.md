# Event Ticketing Platform — Phase 1A: Deep Engineering Analysis

> **Reviewer perspective:** Senior Software Engineer + System Architect + Production Readiness Reviewer  
> **Source:** 5 PDF documents — Sections 2–16, ~270KB of engineering plans  
> **Date of analysis:** April 4, 2026

---

## Table of Contents

1. [System Understanding](#1-system-understanding)
2. [Architecture Review](#2-architecture-review)
3. [Infrastructure Analysis](#3-infrastructure-analysis)
4. [Execution Plan Validation](#4-execution-plan-validation)
5. [Testing & Deployment Review](#5-testing--deployment-review)
6. [Engineering Practices Review](#6-engineering-practices-review)
7. [Troubleshooting & Reliability](#7-troubleshooting--reliability)
8. [System Design Learning Extraction](#8-system-design-learning-extraction)
9. [Gap Analysis](#9-gap-analysis)
10. [Improvements & Recommendations](#10-improvements--recommendations)

---

## 1. System Understanding

### End-to-End System Explanation

This is a **full-stack, production-grade event ticketing platform** built in 3.5 weeks. Think of it as a trimmed Eventbrite clone with Ticketmaster-level concurrency guarantees.

The system allows:
- **OrganizerS** to create events (with multi-tier pricing), publish them, and view analytics
- **Users** to browse, search, and purchase tickets with a 5-minute reservation window
- **Automatic refund, waitlist notification, and QR code delivery** after payment

### Major Components & Interactions

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          FRONTEND (Next.js 14)                          │
│  Home → Event Detail → Cart (5min timer) → Checkout → Confirmation     │
└─────────────────┬───────────────────────────────────────────────────────┘
                  │ HTTP/REST (JWT Bearer)
┌─────────────────▼───────────────────────────────────────────────────────┐
│                     SPRING BOOT API (Java 17)                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────┐  │
│  │  event/  │ │ booking/ │ │ payment/ │ │inventory/│ │notification/│  │
│  │  CRUD    │ │StateMach │ │  Stripe  │ │  Redis   │ │  RabbitMQ   │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬──────┘  │
└───────┼────────────┼────────────┼────────────┼──────────────┼──────────┘
        │            │            │            │              │
┌───────▼────┐ ┌─────▼──────┐ ┌──▼──────┐ ┌──▼──────┐  ┌───▼────────┐
│ PostgreSQL │ │   Redis    │ │ Stripe  │ │ Redis   │  │  RabbitMQ  │
│ (primary   │ │ (locks,    │ │ (hosted │ │ (cache, │  │ (queues,   │
│  source of │ │  cart,     │ │  pay)   │ │ inv.)   │  │  DLQs,     │
│  truth)    │ │  waitlist) │ │         │ │         │  │  email)    │
└────────────┘ └────────────┘ └─────────┘ └─────────┘  └────────────┘
```

### Critical Data Flow — Full Booking Lifecycle

```
1. User selects tier → POST /api/bookings/reserve
   ├── SeatsAvailableGuard (Redis) → blocks if 0 stock
   ├── acquireLock (SET NX EX) → atomic Redis lock
   ├── InventoryService.reserveSeat() → DECR in Redis
   ├── DB: Booking(state=RESERVED, expires_at=now+5min)
   ├── DB: Ticket rows (one per quantity)
   └── Response: { bookingId, expiresAt }

2. Countdown timer starts → User clicks "Checkout"
   → POST /api/bookings/{id}/checkout
   ├── State machine: RESERVED → PAYMENT_PENDING
   ├── Stripe Session.create() → hosted checkout URL
   └── Redirect user to Stripe

3. User pays on Stripe → POST /api/webhooks/stripe
   ├── Signature verify (HMAC-SHA256)
   ├── Idempotency check (ProcessedStripeEvent table)
   ├── State machine: PAYMENT_PENDING → CONFIRMED
   ├── GenerateQRCodeAction (ZXing → Base64 per ticket)
   ├── releaseLock (Lua script ownership check)
   └── RabbitMQ: publish BookingConfirmedEvent (async)

4. RabbitMQ consumer (async, not on critical path)
   ├── EmailService.sendBookingConfirmation()
   ├── QRCodeGeneratorUtil for PDFs
   └── On failure → basicNack → DLQ (not lost)

5. Timer expiry (every 30s @Scheduled)
   ├── Finds RESERVED bookings where expires_at < now
   ├── State machine: RESERVED → EXPIRED
   └── ReleaseSeatsAction → Redis INCR + notifyWaitlist
```

### Architectural Style

**Modular Monolith** — not microservices, not a flat monolith.

- A single deployable JAR with domain-based internal packages
- Each domain (`event/`, `booking/`, `payment/`, `inventory/`, `notification/`, `pricing/`, `user/`) has its own controller → service → repository → model → DTO stack
- Shared infrastructure in `common/` (cross-cutting concerns)
- External async decoupling via RabbitMQ (producer/consumer boundary)
- This is the correct architecture for a 3.5-week solo project; it would be serviceable up to ~10M requests/day before sharding becomes necessary

---

## 2. Architecture Review

### Backend Architecture — Strengths

| Decision | Why It's Good |
|---|---|
| Domain-module package structure | Mirrors domain boundaries; easy to extract to microservices later |
| `ApiResponse<T>` wrapper on all endpoints | Consistent contract; frontend can rely on `{ status, data, error }` shape |
| `GlobalExceptionHandler` (`@ControllerAdvice`) | Single exception mapping location; prevents leaking stack traces |
| Two-layer locking (Redis + `@Version`) | Defense-in-depth against concurrent booking corruption |
| Stateless-per-request State Machine | Avoids race conditions in shared SM instance; DB is authoritative |
| `@EntityGraph` / `JOIN FETCH` for N+1 | Correct fix; explicitly addressed and documented |
| `PricingEngine` as pure stateless service | No I/O, 100% unit testable, fully deterministic |
| Flyway versioned migrations | Schema changes are tracked, repeatable, reviewed in PRs |

### Backend Architecture — Concerns

| Concern | Detail |
|---|---|
| **Spring State Machine overhead** | SSM is a heavyweight library; for 11 states it introduces a lot of boilerplate. A custom FSM with an `enum` switch + `@Transactional` would be simpler and equally safe. SSM choice is valid for learning purposes. |
| **Synchronous QR generation in action** | `GenerateQRCodeAction` runs synchronously during the webhook processing flow. For 100-ticket group bookings this could be slow. Consider offloading QR generation entirely to the RabbitMQ ticket.generation.queue. |
| **Cart stored in Redis only** | If Redis restarts before the user checks out, the cart is lost. A DB fallback or at least a Redis persistence flag (`--appendonly yes` is present but cart TTL is transient) should be acknowledged. |
| **ReservationExpirationJob is a polling job** | `@Scheduled(fixedDelay=30000)` polls every 30s. For a high-volume system, this means expired bookings may sit for up to 30s. Redis key expiry events (keyspace notifications) would be more precise but introduce complexity. Acceptable for Phase 1A. |
| **Single booking per checkout session** | `Payment.booking_id` has a UNIQUE constraint — one payment per booking. This correctly models the domain but means a retry of a failed checkout creates a new Stripe session (old one is abandoned). The `stripe_session_id` on the Booking handles this. |

### Design Patterns Identified

| Pattern | Where |
|---|---|
| **State Machine** | `BookingStateMachineConfig` — explicit transitions, guards, actions |
| **Repository** | All `*Repository` classes via Spring Data JPA |
| **Strategy** | `PricingEngine` — 4 pricing rules applied in sequence |
| **Observer / Event-Driven** | RabbitMQ publishers/listeners |
| **Decorator** | `BookingStateMachineService` wraps the raw SM factory |
| **Template Method** | `BaseIntegrationTest` defines the test container setup; subclasses extend |
| **Chain of Responsibility** | Spring Security filter chain + JWT filter |
| **Factory** | `@EnableStateMachineFactory` — factory pattern forced by the concurrency requirement |
| **Cache-Aside** | `@Cacheable` on `getEventById`, fallback to DB on miss |
| **Idempotency Token** | `ProcessedStripeEvent` table prevents duplicate webhook processing |
| **Optimistic Locking** | `@Version` on `Booking` and `Event` entities |
| **Distributed Lock** | `DistributedLockService` with Lua-script ownership-checked release |
| **Compensating Transaction** | `releaseSeat()` called in catch blocks when downstream calls fail |

### Separation of Concerns — Evaluation

- **Excellent**: `PricingEngine` is isolated with zero dependencies. Pure function, pure testability.
- **Good**: `BookingStateMachineService` wraps the SM; no other service touches the factory.
- **Acceptable**: `StripeWebhookController` handles both signature verification and business logic routing. A `WebhookEventDispatcher` abstraction would be cleaner.
- **Risk**: `BookingNotificationListener` fetches additional data from DB inside the message consumer. This couples the notification path to the DB. The message payload should be fully self-contained (it partially is, via `BookingConfirmedEvent`), reducing the need for additional lookups.

### Scalability & Extensibility

- **Horizontal scaling**: Stateless booking service + Redis-distributed cart state = any instance can handle any request. ✅
- **Adding new event types**: The topic exchange `booking.exchange` supports pattern matching; adding `booking.expired` routing key requires zero config changes to existing consumers. ✅
- **Adding new pricing rules**: `PricingEngine` adds a new `Rule N` method. Single point of change with 100% test coverage mandate. ✅
- **Microservice extraction**: Each domain package already has its own persistence layer. Splitting out `booking/` to a separate service requires: a separate DB, an event bus for cross-domain data, and API gateway routing. The groundwork is there. ✅

---

## 3. Infrastructure Analysis

### Redis — 5 Distinct Use Cases

#### Why It's Used
Redis is used for **6 distinct data patterns**, each chosen because a relational DB cannot match Redis's sub-millisecond atomic operations under high concurrency.

#### How It Integrates

| Use Case | Key Pattern | Data Type | TTL | Critical Design Decision |
|---|---|---|---|---|
| Distributed lock | `seat:lock:tier:{id}:user:{id}` | String | 300s | `SET NX EX` atomic; Lua release script checks ownership before DEL |
| Cart storage | `cart:{userId}` | Hash | 300s | Shares TTL with reservation; cleared on checkout or expiry |
| Event cache | `event:{id}` | String (JSON) | 600s | Cache-aside via `@Cacheable`; evicted on update/publish |
| Event list cache | `events:list:{filterHash}` | String (JSON) | 300s | `allEntries=true` eviction on any event mutation |
| Tier availability | `inventory:tier:{id}:available` | String (int) | No TTL | Seeded at startup from DB; source of truth is DB, Redis is the fast read path |
| Waitlist | `waitlist:{eventId}:{tierId}` | Sorted Set | No TTL | Score = Unix timestamp; `ZPOPMIN` for atomic FIFO dequeue |

#### Optimal Design Evaluation

✅ **Correct**: `SET NX EX` is the right atomic lock acquisition pattern (document explicitly calls out the broken 2-command SETNX+EXPIRE pattern).  
✅ **Correct**: Lua script for release prevents a thread from releasing another thread's lock after TTL expiry.  
✅ **Correct**: Lock key scoped to `tier:{tierId}:user:{userId}` — prevents the same user from double-clicking "Reserve" without blocking all users on that tier.  
✅ **Correct**: Sorted Set for waitlist — `ZPOPMIN` is atomic and returns the earliest waiter without a scan.  
⚠️ **Risk**: Inventory counter has **no TTL**. If a Redis node is restarted without AOF persistence restored correctly, inventory counts return to zero (cache miss falls back to DB, so this is recoverable, but there's a window of inconsistency).  
⚠️ **Risk**: `allEntries=true` on event list eviction is a blunt instrument. On a high-volume platform, publishing one event blows the entire event list cache, causing a thundering herd of DB queries. A tagging-based eviction strategy (cache tag per event) would be more targeted.

#### Missing Considerations

- **Redis Cluster vs single-node**: Document acknowledges this is a known trade-off for Phase 1A. Single-node Redis means the lock is lost if the node crashes mid-purchase. In production, **Redlock over 3+ nodes** is required.
- **Redis persistence**: `--appendonly yes` is configured for the `redis` Docker service. AOF persistence survives container restart but not disk failure.
- **Memory eviction policy**: Not configured. Default is `noeviction`. Under memory pressure, Redis will start rejecting writes — including lock acquisitions. A `volatile-lru` policy on TTL-bearing keys prevents this.
- **Connection pool**: No `lettuce` or `jedis` connection pool tuning is documented. Under 1,000 concurrent Redis operations this could exhaust the connection pool.

---

### RabbitMQ — Async Decoupling Architecture

#### Why It's Used
Email delivery, QR PDF generation, and waitlist notifications are **not on the latency-critical checkout path**. RabbitMQ decouples them: the booking API responds to the user in milliseconds; the email is delivered seconds later asynchronously.

#### How It Integrates

```
StripeWebhookController
        │
        ▼
BookingEventPublisher.publishBookingConfirmation()
        │ JSON via Jackson2JsonMessageConverter
        ▼
booking.exchange (Topic) → routing key: booking.confirmed
        │
        ▼
booking.confirmation.queue  ──[failure]──► booking.confirmation.dlq
        │
        ▼
BookingNotificationListener.handleBookingConfirmation()
        ├── EmailService.sendBookingConfirmation()
        └── channel.basicAck() / basicNack() (manual ACK)
```

#### Optimal Design Evaluation

✅ **Correct**: `durable=true` on exchanges and queues — messages survive broker restart.  
✅ **Correct**: Manual acknowledgement — messages are only removed after successful processing.  
✅ **Correct**: DLQs on all three queues — failed messages are routable, inspectable, and re-queueable.  
✅ **Correct**: `Jackson2JsonMessageConverter` — typesafe POJO-based messaging; no manual JSON parsing.  
✅ **Correct**: `x-message-ttl: 86400000` (24h) — prevents unbounded queue growth during outages.  
⚠️ **Risk**: `basicNack(..., false, false)` sends the message to the DLQ immediately without any retry. In practice, transient failures (SMTP timeout, brief DB hiccup) should be retried 2–3 times before DLQ routing. Configure `x-delivery-limit` + a retry queue with a delay exchange for limited retries.  
⚠️ **Risk**: `notifyNextInWaitlist()` is called inside `ReleaseSeatsAction` (state machine action = synchronous in the transition). If RabbitMQ is briefly unavailable at this point, the action throws and the state transition rolls back. The waitlist notification should itself be a separate RabbitMQ message, not a synchronous inline call.  
⚠️ **Risk**: `BookingNotificationListener` makes a DB call (`fetch booking details`) inside the consumer. If the DB is slow, the consumer blocks and the queue depth grows. Message payloads should be fully self-contained — include all needed data in `BookingConfirmedEvent`.

#### Missing Considerations

- **Retry queue with exponential backoff**: No retry exchange/queue defined. Transient failures go straight to DLQ.
- **Consumer concurrency**: Default is 1 consumer thread per listener. For high-volume notifications, configure `spring.rabbitmq.listener.simple.concurrency=3` and `max-concurrency=10`.
- **Message ordering**: Topic exchange does not guarantee order. For the notification flow this is fine, but document this assumption.
- **Dead letter queue monitoring**: No alerting on DLQ depth is specified. In production, a DLQ alert (CloudWatch / Grafana metric on queue depth > 0) is essential.

---

### Stripe — Payment Integration

#### Why It's Used
Stripe Checkout is the safest integration pattern: the user's card data never touches the platform's servers. PCI compliance is offloaded entirely to Stripe.

#### How It Integrates

```
bookingService.reserveTickets() → RESERVED booking
         │
         ▼
POST /api/bookings/{id}/checkout
         │
PaymentService.createCheckoutSession()
         ├── State machine: RESERVED → PAYMENT_PENDING
         ├── Session.create(SessionCreateParams) → Stripe API
         ├── Persist Payment(status=PENDING, stripeSessionId)
         └── Return { stripeUrl }
         │
User redirected to Stripe-hosted page
         │
Stripe → POST /api/webhooks/stripe (signed webhook)
         ├── Webhook.constructEvent() signature verify
         ├── Idempotency check (ProcessedStripeEvent table)
         ├── checkout.session.completed → CONFIRMED + QR + RabbitMQ
         └── checkout.session.expired → PAYMENT_FAILED + releaseSeat
```

#### Optimal Design Evaluation

✅ **Critical**: The document explicitly states **"The webhook is the only reliable signal — never trust a redirect URL to confirm payment."** This is the most important Stripe integration rule, and it's correctly followed.  
✅ **Correct**: Signature verification via `Webhook.constructEvent()`. Prevents forged payment confirmations from the public internet.  
✅ **Correct**: Idempotency check on `stripe_event_id`. Stripe may deliver the same webhook multiple times; processing twice would double-confirm and double-email.  
✅ **Correct**: Metadata on Stripe session (`{ bookingId, userId }`) — the webhook handler can identify the booking without searching by session ID.  
✅ **Correct**: Booking transitions to `PAYMENT_PENDING` **before** the Stripe session is created. This prevents a second concurrent checkout attempt while the session is open.  
✅ **Correct**: `setExpiresAt(now + 30 minutes)` on the Stripe session — aligns with the 5-minute reservation plus buffer.  
⚠️ **Risk**: The `processedEventRepository.existsByStripeEventId()` check is not wrapped in a unique constraint + `INSERT OR IGNORE` semantics. Two concurrent webhook deliveries of the same event could both pass the existence check before either inserts. The DB-level UNIQUE constraint on `stripe_event_id` is mentioned as "a second guard" — this should be the primary guard.  
⚠️ **Risk**: Refund logic calls `Stripe.refund.create()` synchronously inside `RefundService.processRefund()`. A Stripe API timeout here would leave the booking in `REFUND_REQUESTED` state with no retry mechanism.

#### Missing Considerations

- **Stripe Connect** (organizer payouts): Not in Phase 1A scope. If organizers need to receive funds directly, Stripe Connect requires significant additional flow.
- **Partial payment failure recovery**: If the `Payment` entity save succeeds but the Stripe session creation fails (or vice versa), the booking is in an inconsistent state.
- **3D Secure / SCA handling**: Stripe Checkout handles this automatically when Card type is `4000 0025 0000 3155`. No additional code needed, but this should be tested.
- **Currency support**: Hardcoded to `"usd"`. Internationalization is not addressed.
- **Webhook endpoint CSRF exemption**: Document mentions the endpoint must be excluded from CSRF protection in `SecurityConfig`. This is critical — if missed, all webhooks return 403.

---

### Spring State Machine — Booking Lifecycle Enforcer

#### Why It's Used
Without a state machine, a developer could accidentally call `confirmBooking()` on an `EXPIRED` booking, or approve a refund on a `PAYMENT_PENDING` booking. The SM makes **illegal transitions a runtime exception**, not a silent data corruption.

#### How It Integrates

```
BookingStateMachineConfig (@EnableStateMachineFactory)
         │  defines
         ├── States (11): AVAILABLE → RESERVED → PAYMENT_PENDING → CONFIRMED → ...
         ├── Events (9): ADD_TO_CART, PROCEED_TO_CHECKOUT, PAYMENT_SUCCESS, ...
         ├── Guards: SeatsAvailableGuard, BookingOwnedByUserGuard
         └── Actions: StartReservationTimerAction, ConfirmBookingAction, 
                      ReleaseSeatsAction, GenerateQRCodeAction

BookingStateMachineService (wrapper)
         │  per each request
         ├── getStateMachine(bookingId) from factory
         ├── resetStateMachine(DB state) — DB is authoritative
         ├── sendEvent(BookingEvent) 
         ├── Check result (if false → throw InvalidStateTransitionException)
         ├── persist new state to DB
         └── sm.stop() — release resources
```

#### Optimal Design Evaluation

✅ **Critical design decision**: `@EnableStateMachineFactory` (not `@EnableStateMachine`). A shared singleton SM under concurrent load would have race conditions. The factory-per-request pattern is the correct approach.  
✅ **Correct**: `configure(StateMachineConfigurationConfigurer).autoStartup(false)` — the machine is started manually after state restoration, not on Spring context load.  
✅ **Correct**: DB is the authoritative state store — `resetStateMachine(booking.getState())` forces the machine to the DB-persisted state on every call.  
✅ **Correct**: `@Transactional` wraps the `sendEvent()` + `bookingRepository.save()` sequence. If the DB save fails, the entire operation rolls back.  
⚠️ **Risk**: Actions (`GenerateQRCodeAction`, `ConfirmBookingAction`) run synchronously during the transition. If `generateQrCodes()` for 50 tickets is slow (ZXing encoding), the webhook response is delayed. Consider async action execution for non-critical post-transition side effects.  
⚠️ **Risk**: `sm.stop()` must be in a `finally` block. If the code throws before reaching `stop()`, the machine leaks resources. The document notes this but the implementation risk is real.  
⚠️ **Risk**: Passing context through `ExtendedState.getVariables()` (`bookingId`, `tierId`, `quantity`) is an untyped `Map<Object, Object>`. A type-unsafe API. A typed context object (`BookingContext`) passed via the message headers would be cleaner.

#### Missing Considerations

- No **event persistence** (Spring State Machine's `StateMachinePersister`). State is read from DB and reloaded fresh each time. This is correct for the stateless-per-request pattern but means there's no audit trail of SM events through the SM framework itself.
- **Event status state machine**: Defined (`DRAFT → PUBLISHED → SALES_OPEN → ...`) but its implementation is not backed by Spring State Machine — it appears to be managed by direct service calls (`publishEvent()`, etc.). Inconsistency if the booking SM is SSM but the event lifecycle isn't.
- **Guard failure message**: When `SeatsAvailableGuard` returns `false`, the client receives `InvalidStateTransitionException`. The frontend should show a user-friendly "Sold Out" message, not a raw exception message. HTTP 409 Conflict is the appropriate status code.

---

## 4. Execution Plan Validation

### Timeline Assessment: Is 105–122 Hours Realistic?

**Short answer: Ambitious but achievable for someone with Spring Boot experience; extremely tight for a beginner.**

| Week | Hours | Scope | Risk Assessment |
|---|---|---|---|
| Week 1 (Apr 4–10) | 35–40h | Scaffold + Event CRUD + Auth + Redis + RabbitMQ config + Next.js | **Moderate risk.** Full DB schema, 6 services, Redis/RabbitMQ config, and a running Next.js home page in 7 days assumes no debugging time lost to Docker Compose issues. |
| Week 2 (Apr 11–17) | 35–40h | State Machine + Stripe + RabbitMQ consumers + Redis locking + Frontend pages | **High risk.** The state machine alone (Day 8: 8 hours) includes 5 TDD tests, config, guards, actions, and the first working service method. Stripe integration (Day 9: 7 hours) includes research, tests, CLI setup, and webhook handling. Two of the hardest days are consecutive. |
| Week 3 (Apr 18–24) | 28–32h | Remaining frontend + 80% coverage + k6 + CI/CD + documentation | **Moderate risk.** Coverage push from ~60% to 80% is realistic in one day (Day 16). k6 tuning and CI/CD setup are well-scoped. |

### Sequencing Risks

| Risk | Impact | Justification |
|---|---|---|
| **State Machine on Day 8 (first day of Week 2)** | HIGH | SSM is notoriously tricky to configure. Discovering that `@EnableStateMachineFactory` behaves differently than `@EnableStateMachine` costs 2–3 hours. Scheduling the SM on Saturday (8-hour day) mitigates it — but a bad Saturday means Monday is a recovery day with less slack. |
| **Frontend mixed with backend** | MEDIUM | Day 4 (Tuesday Week 1): Next.js setup + Home Page while the backend is un-deployed locally. The frontend team (same person) must context-switch. Consider pushing to Day 5 or 6. |
| **Stripe CLI dependency** | MEDIUM | Day 9 requires Stripe CLI to be installed and authenticated. If this takes 45 minutes (slow VPN, auth issues), it eats into the 7h day. Should be pre-installed on Day 8 evening during research. |
| **k6 on Day 17 without deployed infra** | LOW | k6 runs against `localhost:8080` for Phase 1A. No cloud dependency. Risk is low. |
| **CI/CD on Day 18 without a working Railway account** | MEDIUM | Railway account creation + PostgreSQL plugin + RAILWAY_TOKEN setup takes 30–60 minutes. Should be done on Day 17 evening or earlier. |

### Bottlenecks

1. **Day 8 (State Machine) → Day 9 (Stripe) back-to-back hard days**: If state machine tests aren't green by end of Day 8, Day 9 Stripe work cannot start cleanly (they depend on `BookingService.reserveTickets()` which depends on the SM).
2. **Test coverage push on Day 16**: Going from ~55% to 80% in one 7h day requires writing ~25 tests across all modules. This is achievable if the Day 16 morning analysis is thorough (JaCoCo red-line identification).
3. **No buffer day**: The plan has zero recovery buffer. Any day running 2+ hours over schedule cascades through all 21 days.

### Recommendations

- Add **30-minute Stripe CLI pre-setup** to Day 8 evening (not Day 9 morning).
- Create **Railway account + DB plugins on Day 14** (during Week 2 cleanup day).
- On Day 6 afternoon, **avoid doing both Testcontainers setup AND N+1 fixes** — two unrelated hard tasks. Put N+1 fixes in Day 7 morning instead.

---

## 5. Testing & Deployment Review

### Testing Strategy Evaluation

#### Test Pyramid Analysis

| Layer | Coverage Target | Tools Used | Verdict |
|---|---|---|---|
| Unit | 80% instruction, 75% branch | JUnit 5 + Mockito + AssertJ | ✅ Correct pyramid base |
| Integration | All critical paths | Testcontainers (PostgreSQL, Redis, RabbitMQ) | ✅ Real infrastructure, not mocks |
| Concurrency | Zero oversell under 100 threads | `CountDownLatch` + `ExecutorService` | ✅ **This is the most important test in the system** |
| Load | P95 < 500ms, 0 double-bookings | k6 | ✅ Correctly placed at Week 3 |
| E2E (UI) | Manual browser testing | Chrome DevTools at 375px | ⚠️ No automated E2E (Playwright/Cypress) |

**Missing Test Types:**
- **Contract tests** (Pact): No API consumer/provider contract validation. If the frontend changes an expected API shape, there's no automated detection.
- **Security tests**: No OWASP ZAP scan or SQL injection testing.
- **Chaos tests**: No failure injection (kill Redis mid-purchase, kill RabbitMQ during message delivery).
- **Mutation testing**: JaCoCo measures coverage but not test quality. A mutation testing tool (PIT) would identify tests that don't actually assert anything meaningful.

#### Critical Tests That Are Well-Designed

- `ConcurrentBookingTest`: 100 threads → exactly 50 succeed = **the gold standard test** for this system. Tests the Redis lock + optimistic locking interaction.
- `PaymentWebhookTest`: Covers invalid signature (HTTP 400) AND duplicate event idempotency — both are production-critical paths.
- `PricingEngineTest`: 100% branch coverage mandate. All 4 pricing rules and 3 refund tiers.
- `NotificationListenerIntegrationTest`: Uses `Awaitility.await()` not `Thread.sleep()`. Correct async assertion pattern.

#### Test Coverage Targets — Assessment

The enforced targets are well-calibrated:

```
PricingEngine:       100% branch  ← Pure function, zero excuse not to
BookingStateMachine: 100% branch  ← 11 states × 9 events = clear specification
RefundService:       100% branch  ← 3 tiers, each is a money calculation
BookingService:       85% branch  ← High business risk, complex flow
Controllers:          65% branch  ← Covered by @WebMvcTest
Config classes:      Excluded     ← Infrastructure wiring, not business logic
```

### Deployment Review

#### Local Environment — Excellent

- 7-service Docker Compose (app, postgres, redis, rabbitmq, mailhog, redis-commander, optional pgAdmin)
- `condition: service_healthy` on all dependencies → no startup race conditions
- `--appendonly yes` on Redis → AOF persistence across container restarts
- HikariCP retry config (30s timeout, 60s initialization-fail-timeout) as second safety net

#### CI/CD Pipeline — Good

```
Push to main/develop → ci.yml
├── Job 1 (test): Postgres + Redis + RabbitMQ as service containers
│   ├── ./mvnw test -Dspring.profiles.active=test
│   └── Upload JaCoCo report as artifact
└── Job 2 (build): docker build -t ticketing-platform:${sha}

Push to main → deploy.yml
└── bervProject/railway-deploy@main → Railway redeployment
```

**Good**: Maven dependency caching with `hashFiles('**/pom.xml')` saves 2–4 minutes per build.  
**Good**: Build job `needs: test` — Docker image is only built if tests pass.  
**Missing**: No image push to a container registry (Docker Hub, GitHub Container Registry). If Railway pulls from source, the Docker build in CI is only a validation step, not the actual deployment artifact. This should be clarified.

#### Rollback Strategy — Gap

**No rollback strategy is defined.** Railway's "Deploy from GitHub" model redeploys from the latest commit. If a bad deployment is pushed:
- There's no documented `git revert` + redeploy procedure
- No Flyway rollback scripts (Flyway Community doesn't support `down` migrations — this is a known limitation)
- No blue/green or canary deployment

**Minimum acceptable rollback**: Document `railway service rollback` command and create a `V{N+1}__rollback_V{N}.sql` migration pattern for schema-breaking changes.

#### Production Risks

| Risk | Severity | Mitigation Present? |
|---|---|---|
| No rollback strategy | HIGH | ❌ Not addressed |
| Single-node Redis | HIGH | ✅ Acknowledged as a known trade-off |
| No rate limiting | HIGH | ❌ Not implemented |
| No DLQ alerting | MEDIUM | ❌ Not configured |
| Stripe webhook duplicate processing race condition | MEDIUM | ✅ DB UNIQUE constraint mentioned |
| Railway free tier sleep on low traffic | LOW | ✅ "Ping periodically" noted |
| `application-local.yml` not in `.gitignore` (secret exposure) | CRITICAL | ✅ Explicitly addressed |

---

## 6. Engineering Practices Review

### TDD — Real or Superficial?

**Verdict: Genuinely Real**

The TDD mandate is enforced through specific test requirements per feature:
- Tests must be written **before** implementation (Red phase)
- Test names follow the **given/when/then** semantic pattern via `@DisplayName`
- The `ConcurrentBookingTest` is inherently TDD — you write it, it fails, you fix the lock until it passes
- `PricingEngine` built TDD with 100% branch coverage means every branch was a Red→Green cycle

**Where TDD is harder to enforce:**
- Frontend (Next.js) has no TDD requirement — only manual browser testing
- Integration tests are written "after the feature is unit-tested" — this is correct but the order dependency relies on discipline
- The Day 6 morning note says "Run the test suite 3 times — it must be deterministic." Non-deterministic tests should be fixed immediately, not retried.

### Clean Code Adherence

The 14-rule checklist is thorough and production-relevant. Notable rules:

| Rule | Rationale Quality |
|---|---|
| No `@Autowired` on fields — constructor injection | ✅ Testability reason given |
| No magic numbers — use named constants | ✅ Maintenance reason given |
| `@Transactional` on service layer only | ✅ Proxy bypass explanation given |
| All exceptions include context | ✅ "Booking not found: id=X" pattern |
| No `System.out.println` — use `@Slf4j` | ✅ Production log aggregator reason |

**Anti-patterns that could still appear:**
- `@Transactional(readOnly = true)` missing on read methods — this enables Hibernate read-only optimization and is commonly forgotten
- `Optional.get()` without `isPresent()` check in repositories (should always use `orElseThrow()`)
- `LocalDateTime` vs `Instant` inconsistency — `expires_at` uses `LocalDateTime` which is timezone-naive. Should use `Instant` or `OffsetDateTime`.

### Git & PR Practices — Excellent

- Feature branch per feature, protected `main`
- Conventional commits with 9 typed prefixes
- PR template with 5 sections (What/Why/Key Decisions/How to Test/Tests Added/Coverage Delta)
- Coverage delta in every PR — this forces coverage awareness per PR, not just at the end

---

## 7. Troubleshooting & Reliability

### The 7 Documented Failure Scenarios

| # | Scenario | Severity | Root Cause Accuracy | Fix Quality |
|---|---|---|---|---|
| 1 | Stripe webhook not receiving events | HIGH | ✅ Correct (localhost not public) | ✅ Stripe CLI as primary, ngrok as backup |
| 2 | RabbitMQ connection refused on startup | HIGH | ✅ Correct (startup ordering) | ✅ `condition: service_healthy` fix |
| 3 | Redis lock deadlock — lock never released | HIGH | ✅ Correct (`finally` block missing) | ✅ Redis CLI TTL inspect + `executeWithLock()` pattern |
| 4 | N+1 query still present after fix | MEDIUM | ✅ Correct (self-invocation bypasses proxy) | ✅ 3 different fix approaches given |
| 5 | `@Transactional` not rolling back | MEDIUM | ✅ Correct (external services excluded from TX) | ✅ Compensating transactions + `@Retryable` |
| 6 | Docker Compose services start out of order | MEDIUM | ✅ Correct (`depends_on` without `condition`) | ✅ `condition: service_healthy` + HikariCP retry |
| 7 | `ObjectOptimisticLockingFailureException` | LOW | ✅ Correct (expected behavior, not a bug) | ✅ `@Retryable` with exponential backoff |

### Missing Failure Scenarios

| Missing Scenario | Why It Should Be Documented |
|---|---|
| **Stripe webhook body read twice** | `HttpServletRequest.getInputStream()` can only be read once. A logging filter that reads the request body before the controller breaks signature verification. Document `ContentCachingRequestWrapper`. |
| **State machine in inconsistent state** | If `bookingRepository.save()` throws after state machine transition succeeds, the DB still shows the old state. Solution: wrap in `@Transactional`. This is mentioned but not in the troubleshooting guide. |
| **RabbitMQ message stuck in DLQ** | No documented procedure for inspecting, reprocessing, or discarding DLQ messages. This is a daily operational task in production. |
| **HikariCP pool exhaustion** | Documented in k6 tuning as "if pool exhaustion occurs, increase max-pool-size to 20". This is light — document symptoms (slow queries, timeout exceptions), not just the fix. |
| **Flyway migration conflict on existing DB** | Documented in deployment (add `baseline-on-migrate=true`). Should also cover the "checksum mismatch" error (occurs when you edit a migration file after it's been applied). |
| **JWT token clock skew** | If the server clock diverges from the client, JWTs may expire early or be rejected. Document clock synchronization (`ntpd`) as a production requirement. |

### System Resilience Assessment

| Resilience Property | Status |
|---|---|
| **Retry on transient failures** | ✅ `@Retryable` on `OptimisticLockingFailureException` (3 attempts, exponential backoff) |
| **Circuit breaker** | ❌ None. If Stripe API is degraded, every checkout call will hang until timeout. |
| **Fallback for Redis unavailable** | ⚠️ Partial. `getAvailableCount()` falls back to DB on cache miss. Lock acquisition throws `LockAcquisitionException` — no fallback. |
| **Message durability** | ✅ Durable queues, exchanges, 24h TTL, DLQ |
| **Health checks** | ✅ `/actuator/health` on all services |
| **Graceful shutdown** | ⚠️ Not documented. Spring Boot's graceful shutdown (`spring.lifecycle.timeout-per-shutdown-phase=30s`) should drain in-flight requests before pod termination. |

---

## 8. System Design Learning Extraction

### What a Student Should Learn From This Project

#### Distributed Systems Concepts

| Concept | Where It Appears | Interview Relevance |
|---|---|---|
| **Distributed locking** | `DistributedLockService` (SET NX EX, Lua release) | ⭐⭐⭐⭐⭐ Asked constantly in FAANG interviews |
| **Race condition prevention (TOCTOU)** | Double-check availability under lock | ⭐⭐⭐⭐⭐ Core to every concurrent system problem |
| **Optimistic vs pessimistic locking** | `@Version` (optimistic) vs Redis lock (pessimistic) | ⭐⭐⭐⭐⭐ Classic database interview topic |
| **Idempotency** | `ProcessedStripeEvent` table | ⭐⭐⭐⭐ Every payment API interview question |
| **At-least-once delivery** | RabbitMQ manual ACK, DLQ | ⭐⭐⭐⭐ Messaging system design questions |
| **CAP theorem trade-offs** | Redis single-node (CP over AP), acknowledged | ⭐⭐⭐⭐ Every distributed system interview |
| **Eventual consistency** | Email sent asynchronously via RabbitMQ | ⭐⭐⭐ Real-world distributed systems |
| **Cache invalidation** | `allEntries=true` vs key-specific eviction | ⭐⭐⭐ Caching pattern questions |
| **State machine as specification** | BookingStateMachineConfig — illegal transitions = runtime error | ⭐⭐⭐ Domain modeling questions |

#### Backend Engineering Concepts

| Concept | Where It Appears | Depth |
|---|---|---|
| **N+1 query detection and fix** | Day 6, `@EntityGraph`, `JOIN FETCH` | Deep — `EXPLAIN ANALYZE` output documented |
| **Compensating transactions** | `releaseSeat()` in catch blocks when Stripe fails | Medium |
| **Connection pool tuning** | HikariCP `maximum-pool-size, connection-timeout` | Practical |
| **Webhook signature verification** | `Webhook.constructEvent()` | Critical for any payment integration |
| **Multi-stage Docker builds** | JDK builder → JRE runtime, non-root user | Production best practice |
| **Testcontainers** | Real DB/Redis/RabbitMQ in tests | Modern integration testing |
| **Flyway migrations** | Versioned, sequential, never edited after apply | Database change management |

#### System Design Interview Preparation

**Design Ticketmaster** — you can now answer every question from personal experience:

| Interview Question | Answer From This Project |
|---|---|
| "How do you prevent overselling?" | Redis SET NX EX distributed lock + @Version optimistic locking fallback |
| "Why Redis locks over DB locks?" | DB row locks hold a connection for lock duration; at 50K QPS, pool exhaustion. Redis is in-memory, sub-millisecond, independent pool. |
| "What's the critical path in a ticket purchase?" | Lock acquisition → inventory decrement → DB write → response. Everything else (email, QR) is async. |
| "How does the booking state machine prevent illegal transitions?" | `sendEvent()` returns false on illegal transitions; the wrapper throws `InvalidStateTransitionException`. |
| "How do you handle payment webhook idempotency?" | `ProcessedStripeEvent` table with UNIQUE constraint on `stripe_event_id`. |
| "What happens if Redis crashes during a ticket purchase?" | Lock is lost; `@Version` is the fallback. With Redlock (3 nodes), the lock survives single-node failure. |

---

## 9. Gap Analysis

### What's Missing to Be Production-Grade

#### 🔴 Security Gaps (Critical)

| Gap | Impact | Fix |
|---|---|---|
| **No rate limiting** | Bot flooding during popular event sales overwhelms the booking API | API Gateway or `spring-boot-starter-actuator` rate limiting; Bucket4j for method-level |
| **No HTTPS enforcement** | Man-in-the-middle attacks on JWT tokens | Railway auto-provisions TLS; local dev should use `https://localhost` or trust self-signed |
| **JWT stored in `localStorage`** | XSS vulnerability — stolen JWT = stolen session | Use `HttpOnly` secure cookie instead of `localStorage` for JWT storage |
| **No OWASP security headers** | Missing `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy` | Spring Security `.headers()` configuration |
| **SQL injection** | JPA/Hibernate with named parameters is safe; JPQL `@Query` with `@Param` is safe. Raw JDBC is not used. | ✅ Low risk, but confirm all custom `@Query` annotations use named parameters, not string concatenation |
| **QR code signature** | QR payload is signed with "app secret" (vague). If the secret leaks, QR codes can be forged. | Use RSA asymmetric signing: private key signs, public key verifies at check-in. |

#### 🔴 Observability Gaps (Critical)

| Gap | Impact | Fix |
|---|---|---|
| **No structured logging** | Log lines are unqueryable in log aggregators | Use Logstash/JSON appender with correlation IDs per request |
| **No distributed tracing** | Cannot trace a single booking request across state machine transitions, Redis calls, and RabbitMQ messages | Spring Micrometer Tracing + Zipkin or Jaeger |
| **No metrics dashboard** | No visibility into booking throughput, error rates, P95 latency | Spring Actuator → Micrometer → Prometheus → Grafana |
| **No alerting** | DLQ depth > 0, error rate > 1%, P95 > 500ms — none of these trigger alerts | PagerDuty / Alertmanager rules |
| **No correlation ID propagation** | A request that spans Booking API → RabbitMQ → Notification Listener has no common trace ID | MDC correlation ID, propagated via RabbitMQ message headers |

#### 🟡 Performance Concerns

| Concern | Impact | Fix |
|---|---|---|
| **Event list cache invalidation stampede** | Publishing one event blows entire event list cache (1,000 cached filter combinations). All users get cache misses simultaneously, overwhelming the DB. | Cache tagging; or accept the trade-off for Phase 1A (short TTL = small window). |
| **Synchronous QR generation in webhook** | 50-ticket group booking = 50 ZXing encodings in the webhook handler = slow response | Offload to `ticket.generation.queue` |
| **`@Scheduled` polling job every 30s** | Inefficient; queries the DB for all RESERVED bookings every 30s even if there are none | Redis keyspace notification (passive key expiry event) or a dedicated delay queue |
| **No DB read replica for event browsing** | Event search queries run against the primary. Under 1,000 concurrent users, this competes with booking writes. | Configure `@Transactional(readOnly=true)` → route to replica via Spring `AbstractRoutingDataSource` |

#### 🟡 Data Consistency Issues

| Issue | Scenario | Fix |
|---|---|---|
| **Redis inventory ↔ DB divergence** | After Redis restart without AOF, inventory counts must be reloaded from DB at startup. The startup warming is present, but the window between restart and warm-up is unprotected. | Add a Redis startup health check that blocks new reservations until the inventory warm-up completes. |
| **Booking state ↔ Stripe state divergence** | If the Stripe webhook fires but the Spring Boot app is restarting, the event is lost (unless Stripe retries). | Stripe retries webhooks on non-200 response; ensure the app returns 200 only after DB commit. |
| **Inventory count vs available_count column** | `InventoryService` decrements the Redis counter; the DB `available_count` column is decremented when the `Booking` is persisted. These two operations are not atomic — a crash between them leaves Redis and DB diverged. | Make Redis the read-optimistic cache and treat DB `available_count` as authoritative; reconcile on startup and periodically. |

---

## 10. Improvements & Recommendations

### Priority Matrix

#### 🔴 HIGH IMPACT — Implement Before Production Launch

| # | Improvement | Why Critical |
|---|---|---|
| H1 | **Add rate limiting at the API Gateway level** | Without this, a 50K-user ticket rush allows bots to place reservations faster than humans. Use Bucket4j or an API Gateway (Kong, AWS API Gateway). | 
| H2 | **Implement Redlock over 3 Redis nodes** | Single-node Redis is the acknowledged critical risk. A node crash mid-purchase = double-sell. Redlock is the production-grade solution. |
| H3 | **Add structured logging with correlation IDs** | `log.info("Processing booking: " + bookingId)` cannot be queried in production. JSON logging with `traceId`, `spanId`, `bookingId` as structured fields is essential for debugging. |
| H4 | **Move JWT from localStorage to HttpOnly cookie** | XSS attacks can steal `localStorage` tokens. `HttpOnly` cookies are immune to JavaScript access. |
| H5 | **Prometheus + Grafana metrics** | Add Micrometer metrics for: booking conversion rate, lock acquisition failure rate, DLQ depth, P95 latency per endpoint. Deploy Grafana dashboard before go-live. |
| H6 | **Define rollback procedure** | Document: `railway service rollback`, Flyway compensation migrations, and a manual "freeze" procedure to stop new bookings during incident response. |

#### 🟡 MEDIUM IMPACT — Implement in Phase 1B or as Technical Debt

| # | Improvement | Why Important |
|---|---|---|
| M1 | **Add retry queue with exponential backoff for RabbitMQ** | Currently `basicNack` sends straight to DLQ. Transient failures (SMTP timeout) should be retried 2–3 times before DLQ routing. Configure a retry exchange with `x-message-ttl` and `x-dead-letter-exchange`. |
| M2 | **Offload QR generation to ticket.generation.queue** | `GenerateQRCodeAction` is synchronous in the webhook handler. Large group bookings will slow webhook response. Generate QR codes asynchronously; the confirmation page can poll for them. |
| M3 | **Circuit breaker on Stripe calls** | Use Resilience4j `@CircuitBreaker`. If Stripe returns 5 consecutive errors, open the circuit (fail fast with a clear "payment temporarily unavailable" message) rather than letting all requests hang until HTTP timeout. |
| M4 | **`@Transactional(readOnly = true)` on all read methods** | Read-only transaction hint allows Hibernate to skip dirty checking, enables connection routing to read replicas, and makes intent explicit. |
| M5 | **Replace `LocalDateTime` with `Instant`** | `expires_at`, `booking_date`, etc. should use `Instant` (UTC epoch reference) not `LocalDateTime` (ambiguous timezone). Serialization across timezones would give wrong expiry windows. |
| M6 | **Add `X-Correlation-ID` request header propagation** | On every request, read or generate a correlation ID, add it to MDC, propagate it to RabbitMQ message headers. All log lines in the request chain share the same ID. Invaluable for production debugging. |
| M7 | **Implement check-in API** (`POST /api/events/{id}/check-in`) | The state machine defines `CHECK_IN → ATTENDED` but the check-in endpoint is listed as a "Day 21 feature" with no implementation detail. Idempotent check-in with `SELECT FOR UPDATE` on the ticket row is the correct pattern. |

#### 🟢 LOW IMPACT — Good-to-Have Improvements

| # | Improvement | Benefit |
|---|---|---|
| L1 | **Mutation testing with PIT** | JaCoCo measures *whether* a test covers a line; PIT measures *whether* the test actually fails when the code changes. 80% JaCoCo coverage with poor assertions != quality tests. |
| L2 | **API versioning** (`/api/v1/...`) | When the API contract changes (adding required fields), versioned endpoints prevent breaking existing frontend contracts. |
| L3 | **Organizer-configurable refund policy** | Phase 1A has a global 3-tier refund rule. Eventbrite allows organizer-defined policies. Add a `RefundPolicy` entity linked to `Event`. Phase 1B candidate. |
| L4 | **Playwright E2E tests** | The full booking flow (reserve → cart → Stripe → confirmation) is currently only manually tested. A Playwright test suite would automate the regression check. |
| L5 | **Soft delete on bookings** | `DELETE /api/bookings/{id}` currently triggers the cancellation flow. For audit compliance, bookings should never be hard-deleted. Use a `deleted_at` soft-delete column. |
| L6 | **Event cover image upload to S3** | `cover_image_url` accepts a URL string. In production, organizers need to upload files. Add S3 presigned URL generation endpoint. |

---

## Summary — The Strongest and Weakest Parts

### ⭐ Strongest Aspects

1. **Two-layer concurrency protection** (Redis lock + `@Version`) — correctly motivated, correctly implemented, correctly tested. The `ConcurrentBookingTest` is the most important test in the system.
2. **State machine design** — using `@EnableStateMachineFactory` + stateless-per-request pattern is the correct Spring SM usage. Most tutorials get this wrong (singleton SM, race conditions).
3. **Stripe webhook pattern** — "webhook is the only reliable signal" is a production truth that many developers get wrong. The idempotency check on `stripe_event_id` shows maturity.
4. **Redis key design** — scoped lock keys (`tier:{id}:user:{id}` not just `tier:{id}`), correct data types (Sorted Set for waitlist, INCR/DECR for inventory), Lua release script.
5. **Test class inventory** — 13 test classes covering all layers including a real concurrency test and Awaitility-based async consumer test.

### ⚠️ Weakest Aspects

1. **No observability** — no structured logging, no distributed tracing, no metrics dashboard. You could not debug a production incident with the current setup.
2. **No rate limiting** — a design for 50K concurrent users with no request throttling is incomplete for real ticket rush scenarios.
3. **Single-node Redis** — acknowledged but the mitigation path (Redlock) is not explored in the implementation guides. For Phase 1B, this must be elevated to implementation, not just documentation.
4. **No rollback strategy** — Flyway doesn't support down migrations; no documented emergency stop-loss procedure.
5. **Synchronous QR generation in webhook handler** — unacceptable for group bookings; should be async via the existing `ticket.generation.queue`.

---

*Analysis complete — April 4, 2026 | Covers all 16 sections of Phase 1A documentation*
