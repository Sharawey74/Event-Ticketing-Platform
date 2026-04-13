# Event Ticketing Platform — GitHub Copilot Master Instructions

> This file is ALWAYS active for every file in this project.
> It establishes project-level context that all instruction files and skills build upon.

## Project Identity

- **Project:** Event Ticketing Platform (Phase 1A)
- **Stack:** Java 21, Spring Boot 3.x, PostgreSQL 17, Redis 7, RabbitMQ 4.2.5, Stripe, Next.js 14
- **Architecture:** Modular monolith — each domain is independently extractable to a microservice
- **Timeline:** April 4–24, 2026 | 21 days | 105–122 hours
- **Base package:** `com.ticketing`

## Architecture Decisions (Locked — Do Not Deviate)

### Dependency Injection
- **ALWAYS use constructor injection** via Lombok `@RequiredArgsConstructor`
- **NEVER use `@Autowired` field injection** — it makes testing impossible and hides dependencies
- All dependency fields must be `private final`

### Time Handling
- **ALWAYS use `java.time.Instant` (UTC)** for all timestamps stored in the database
- **NEVER use `LocalDateTime`** for time-sensitive fields — it has no timezone and will cause DST bugs
- Flyway migrations must use `TIMESTAMPTZ` (not `TIMESTAMP`) for all date/time columns

### Concurrency and Locking
- **Two-layer protection is mandatory for inventory operations:**
  1. Redis distributed lock (`SET key value NX EX 300`) — acquired BEFORE the DB transaction
  2. JPA `@Version` optimistic locking on the `Booking` entity — fallback if Redis fails
- **TOCTOU prevention:** Always re-check availability INSIDE the distributed lock body, not before acquiring it
- **Lua scripts for atomic Redis operations** — never use multi-step Redis commands (SETNX then EXPIRE is a race condition)

### Database
- **PostgreSQL is the source of truth** — Redis is the fast cache/lock layer only
- Use `ENUM` types in PostgreSQL for role fields (not VARCHAR) — prevents silent bad data
- Use soft delete (`deleted_at TIMESTAMPTZ`) on bookings — never hard delete

### Payment (Stripe)
- **Return HTTP 200 to Stripe ONLY after the DB transaction commits** — the webhook controller must NOT be `@Transactional`
- **Idempotency at the DB layer** — a UNIQUE constraint on `processed_stripe_events.stripe_event_id` is the real guard, not an `existsBy()` check
- **Never trust redirect URLs for payment confirmation** — webhook only

### Messaging (RabbitMQ)
- **QR code generation must be async** — offloaded to `ticket.generation.queue`, never inline in the webhook handler
- All message consumers must propagate `X-Correlation-ID` via MDC

## Package Naming Convention (Exact — No Deviations)

```
Base package:           com.ticketing
Domain packages:        com.ticketing.{domain}.controller
                        com.ticketing.{domain}.service
                        com.ticketing.{domain}.repository
                        com.ticketing.{domain}.model
                        com.ticketing.{domain}.dto
Valid domains:          event, booking, payment, inventory, notification, pricing, user, checkin
Shared infrastructure:  com.ticketing.common.config
                        com.ticketing.common.filter
                        com.ticketing.common.exception
                        com.ticketing.common.util
State machine:          com.ticketing.booking.statemachine (config, guards, actions)
Security:               com.ticketing.common.security (JwtService, JwtFilter, SecurityConfig)
```

## Business Constants (Always Use — No Magic Numbers)

All hardcoded business values must come from `BusinessConstants.java` in `com.ticketing.common.util`:

| Constant | Value | Description |
|---|---|---|
| `RESERVATION_TTL_SECONDS` | 300 | 5-minute reservation window |
| `LOCK_TTL_SECONDS` | 300 | Redis lock TTL |
| `EARLY_BIRD_DAYS_THRESHOLD` | 30 | Days before event for early bird |
| `EARLY_BIRD_DISCOUNT` | 0.50 | 50% early bird discount |
| `GROUP_DISCOUNT_MIN_QUANTITY` | 5 | Minimum tickets for group rate |
| `GROUP_DISCOUNT_RATE` | 0.10 | 10% group discount |
| `DYNAMIC_PRICING_THRESHOLD` | 0.80 | 80% sold triggers surge |
| `DYNAMIC_PRICING_SURGE` | 0.25 | 25% surge increase |
| `FULL_REFUND_DAYS_THRESHOLD` | 7 | >= 7 days = full refund |
| `PARTIAL_REFUND_DAYS_THRESHOLD` | 3 | 3-6 days = 50% refund |
| `PARTIAL_REFUND_RATE` | 0.50 | 50% partial refund |
| `EXPIRY_JOB_INTERVAL_MS` | 30_000 | 30-second expiry job interval |

## Critical Technical Components

These are the most complexity-dense parts of the system. Handle with extra care:

- **`DistributedLockService`** — Redis Lua script for ownership-checked lock release
- **`BookingStateMachineConfig`** — Use `@EnableStateMachineFactory`, NOT `@EnableStateMachine`; factory-per-request prevents shared singleton race conditions
- **`InventoryService.reserveSeat()`** — Lua floor guard prevents negative inventory: check AND decrement in one atomic Lua call
- **`StripeWebhookController`** — NOT `@Transactional`; idempotency via DB UNIQUE constraint

## Logging and Observability

- Use SLF4J only: `private static final Logger logger = LoggerFactory.getLogger(MyClass.class);`
- Never use `System.out.println()` or concrete implementations
- Always propagate `X-Correlation-ID` header via MDC in all HTTP requests and RabbitMQ messages
- Use parameterized logging: `logger.info("Booking {} reserved by user {}", bookingId, userId);`

## Code Quality Rules

- No method body exceeding 20 lines (extract helper methods)
- No raw String literals for status values — always use enums
- Constructor injection only — no field injection
- `@Transactional(readOnly = true)` on ALL read-only service methods (class-level default, override for writes)
- `@PreAuthorize` on all sensitive controller methods
- DTOs for all API boundaries — never expose JPA entities directly

## Testing Standards

- Unit tests: JUnit 5 + Mockito, Arrange-Act-Assert pattern
- Integration tests: `@SpringBootTest` + Testcontainers (real PostgreSQL, real Redis, real RabbitMQ)
- Test naming: `methodName_when_condition_should_expectedBehavior`
- The concurrent booking test (100 threads, 50 tickets) must always pass
