# Day 1 — Session Prompt
**Date:** Saturday, April 4, 2026 | **Planned Hours:** 8 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 1 — Project Initialization.
Feature: project-scaffold

Active fixes today:
- Fix 1.1 — CRITICAL: ALL Instant not LocalDateTime; TIMESTAMPTZ in SQL
- Fix 1.2 — IMPORTANT: user_role as PostgreSQL ENUM, not VARCHAR
- Fix 1.3 — GOOD: deleted_at TIMESTAMPTZ on bookings table
- Fix CC-2 — IMPORTANT: BusinessConstants.java created on Day 1
- Fix CC-1 — GOOD: CorrelationIdFilter with MDC propagation

Pre-conditions: This is Day 1 — starting from scratch.
Docker Desktop is running. ./mvnw is available.

Day 1 has no service logic, so no TDD loop today.
Instead: confirm ./mvnw compile passes after each step before moving forward.

Non-negotiable rules:
- All entities use Instant (zero LocalDateTime)
- All TIMESTAMPTZ in Flyway SQL (zero TIMESTAMP WITHOUT TIME ZONE)
- user_role is CREATE TYPE ... AS ENUM
- BusinessConstants.java created before any service code
- Zero @Autowired anywhere

Start with Step 1 (Spring Boot project generation). Confirm the full
pom.xml before writing any Java files.
```

---

## PRE-SESSION CHECKLIST (Do before opening VS Code)
```
[ ] Native Windows PostgreSQL and RabbitMQ services are DISABLED (Check services.msc)
[ ] WSL Redis is STOPPED (sudo service redis-server stop)
[ ] Docker Desktop is OPEN and RUNNING (check system tray icon is green)
    Without this: ./mvnw test will fail with pipe error
[ ] Verify in PowerShell: java -version → must show 21.x
[ ] Read this full prompt before starting
```

---

## Context Briefing

**What we're building today:**
Day 1 is the most foundational day. We lay the project skeleton from which all 20 subsequent days grow. The goal is NOT to write complex business logic — it is to create a production-grade scaffold that compiles, connects to Docker-hosted services, and already applies the project's architectural standards from line one.

**Why this day is critical:**
Every class, migration, and entity written today sets the standard everything else follows. Shortcuts or inconsistencies introduced on Day 1 (wrong time types, wrong injection style, no constants file) will propagate across 20 days of code before being caught.

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 1 (ExecutionMap)
- **Architecture reference:** Section 3 — Module structure + Section 4 — Package layout
- **Schema reference:** Section 5 — Database tables + ERD
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`
- **Secondary plan file:** `Plans/Text/Phase1A_Sections 3,4,5_FullStructure.txt`

---

## Fixes to Apply Today

Cross-reference `Plans/Phase1A_Adjustments_and_Fixes.md` for full details:

| Fix ID | Severity | Action Required |
|--------|----------|-----------------|
| **Fix 1.1** | 🔴 CRITICAL | Replace ALL `LocalDateTime` with `Instant` in every entity. Change Flyway SQLs to use `TIMESTAMPTZ` not `TIMESTAMP WITHOUT TIME ZONE`. |
| **Fix 1.2** | 🟡 IMPORTANT | Create `user_role` as a PostgreSQL ENUM type in V1 migration. Do NOT use VARCHAR. |
| **Fix 1.3** | 🟢 GOOD | Add `deleted_at TIMESTAMPTZ` column to the bookings table in V5 migration. Add `@Where(clause = "deleted_at IS NULL")` on the Booking entity. |
| **Fix CC-2** | 🟡 IMPORTANT | Create `BusinessConstants.java` in `com.ticketing.common.util` on Day 1, before writing any service that touches inventory, pricing, or locks. |
| **Fix CC-1** | 🟢 GOOD | Create `CorrelationIdFilter.java` in `com.ticketing.common.filter` — propagates `X-Correlation-ID` header via MDC on every request. Register it as a `@Bean` in a `WebConfig` class. |

---

## Tasks (In Order)

### Morning (2 hrs) — System Design + ERD
1. Read *Designing Data-Intensive Applications* Ch. 1 — take 5 bullet notes
2. Sketch full DB schema (9 tables, relationships, indexes) — ERD v1

### Afternoon (5 hrs) — Spring Boot Setup + Schema

#### Step 1 — Spring Boot Project
- Generate at `start.spring.io`: Web, Data JPA, Security, AMQP, Data Redis, Flyway, PostgreSQL, Lombok, Validation, Actuator, Testcontainers (test scope)
- Add to `pom.xml` manually: `stripe-java:23.3.0`, `spring-statemachine-core:3.2.0`, `jjwt:0.11.5` (api/impl/jackson), `spring-retry`, `spring-boot-starter-cache`, `com.google.zxing:core:3.5.2`, `com.google.zxing:javase:3.5.2`
- Project settings: `name=ticketing-platform`, `group=com.ticketing`, **Java 21**

#### Step 2 — Package Structure Definition
Acknowledge the target package structure (do NOT create empty stub classes like `@Service` or `@RestController`, this violates our incremental rule. Packages will be created as needed when entities and configs are added today):
```
com.ticketing.event.controller
com.ticketing.event.service
com.ticketing.event.repository
com.ticketing.event.model
com.ticketing.event.dto
com.ticketing.booking.controller / service / repository / model / dto
com.ticketing.booking.statemachine (config, guards, actions)
com.ticketing.payment.controller / service / repository / model
com.ticketing.inventory.service / repository
com.ticketing.notification.service / listener
com.ticketing.pricing.service
com.ticketing.user.controller / service / repository / model
com.ticketing.checkin.controller / service
com.ticketing.common.config
com.ticketing.common.filter
com.ticketing.common.exception
com.ticketing.common.util
com.ticketing.common.security
```
**Verify: `./mvnw compile` passes with zero errors before proceeding.**

#### Step 3 — BusinessConstants (Fix CC-2 — Apply NOW)
Create `com.ticketing.common.util.BusinessConstants` immediately — all future services import from here:
```java
public final class BusinessConstants {
    private BusinessConstants() {}
    public static final long RESERVATION_TTL_SECONDS     = 300L;
    public static final long LOCK_TTL_SECONDS            = 300L;
    public static final int  EARLY_BIRD_DAYS_THRESHOLD   = 30;
    public static final double EARLY_BIRD_DISCOUNT       = 0.50;
    public static final int  GROUP_DISCOUNT_MIN_QUANTITY = 5;
    public static final double GROUP_DISCOUNT_RATE       = 0.10;
    public static final double DYNAMIC_PRICING_THRESHOLD = 0.80;
    public static final double DYNAMIC_PRICING_SURGE     = 0.25;
    public static final int  FULL_REFUND_DAYS_THRESHOLD  = 7;
    public static final int  PARTIAL_REFUND_DAYS_THRESHOLD = 3;
    public static final double PARTIAL_REFUND_RATE       = 0.50;
    public static final int  EXPIRY_JOB_INTERVAL_MS      = 30_000;
}
```

#### Step 4 — Flyway Migrations (Fix 1.1 + Fix 1.2)
Write all 9 migration files under `src/main/resources/db/migration/`:
- `V1__create_users_table.sql` — use `CREATE TYPE user_role AS ENUM ('USER', 'ADMIN', 'ORGANIZER')` (Fix 1.2). Use `TIMESTAMPTZ` for all date columns (Fix 1.1).
- `V2__create_venues_and_categories.sql`
- `V3__create_events_table.sql` — `TIMESTAMPTZ` for `start_date`, `end_date`, `created_at`, `updated_at`
- `V4__create_ticket_tiers.sql`
- `V5__create_bookings_and_tickets.sql` — include `deleted_at TIMESTAMPTZ` (Fix 1.3) and `version BIGINT DEFAULT 0` for optimistic locking
- `V6__create_payments_and_refunds.sql` — include `processed_stripe_events` table with UNIQUE on `stripe_event_id`
- `V7__create_indexes.sql`
- `V8__add_event_features.sql` — `waitlist_enabled BOOLEAN`, `dynamic_pricing_enabled BOOLEAN`
- `V9__seed_data.sql` — 5 categories, 3 venues

#### Step 5 — JPA Entities (Fix 1.1)
Write all entities: `User`, `Event`, `Category`, `Venue`, `TicketTier`, `Booking`, `Ticket`, `Payment`, `Refund`
- **ALL timestamp fields: `Instant`, never `LocalDateTime`**
- `Booking` entity must have: `@Version Long version`, `deleted_at Instant`
- Use Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

#### Step 6 — Docker Compose
```yaml
# docker-compose.yml — Fully Dockerized infrastructure
services:
  postgres:
    image: postgres:17              
    environment:
      POSTGRES_DB: ticketing_db
      POSTGRES_USER: ticketing
      POSTGRES_PASSWORD: ticketing
    ports:
      - "5432:5432"                # Standard port
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7                  
    ports:
      - "6379:6379"                # Standard port
    command: redis-server --save 20 1 --loglevel warning

  rabbitmq:
    image: rabbitmq:4-management    
    ports:
      - "5672:5672"                # Standard port
      - "15672:15672"              # management UI
    environment:
      RABBITMQ_DEFAULT_USER: ticketing
      RABBITMQ_DEFAULT_PASS: ticketing

  mailhog:
    image: mailhog/mailhog
    ports:
      - "1025:1025"               # SMTP
      - "8025:8025"               # Web UI

volumes:
  pgdata:
```

**application-local.yml** (used when running natively via `./mvnw spring-boot:run`):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ticketing_db
    username: ticketing
    password: ticketing
  data:
    redis:
      host: localhost
      port: 6379   
  rabbitmq:
    host: localhost
    port: 5672
    username: ticketing
    password: ticketing
```

#### Step 7 — CorrelationIdFilter (Fix CC-1)
Create `com.ticketing.common.filter.CorrelationIdFilter` implementing `OncePerRequestFilter`:
- Extract `X-Correlation-ID` header on every request
- Put in MDC: `MDC.put("correlationId", id)`
- Clear MDC in `finally` block
- Register as `@Bean FilterRegistrationBean` in `WebConfig`

### Evening (1 hr) — TDD Setup + Git
- Create `EventServiceTest` with 3 failing test stubs
- `git init` → `.gitignore` → first commit: `chore: initialize spring boot project with full package structure`
- Create GitHub repo and push
- Add `README.md` skeleton: `docs: add readme skeleton`

---

## Expected Deliverable / Success Criteria

```
[ ] ./mvnw compile — zero errors
[ ] ./mvnw spring-boot:run — app starts, connects to DB, Flyway runs all 9 migrations
[ ] docker-compose up -d postgres redis rabbitmq — all 3 services running
[ ] docker ps — shows postgres, redis, rabbitmq healthy
[ ] BusinessConstants.java created in common.util
[ ] ALL entities use Instant, NO LocalDateTime
[ ] user_role is a PostgreSQL ENUM (not VARCHAR)
[ ] deleted_at column on bookings table
[ ] CorrelationIdFilter registered and active
[ ] GitHub repo created and initial commit pushed
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`
- `Plans/skills/sql-optimization.SKILL.md`

## Handoff Protocol
At the end of this session, the AI must print:
```
--- SESSION HANDOFF ---
Day 1 status     : [In Progress / Complete]
Fixes applied    : [list]
Fixes pending    : [list]
Files created    : [list]
Files modified   : [list]
Next session start: [exact task]
-----------------------
```
