# AI CONTEXT SNAPSHOT — Event Ticketing Platform

## Last Updated: Day 11 (2026-05-29) — Pricing Engine & Waitlist Complete

## Branch: day-12-refund-concurrency

## Test Status: 88/88 passing (PostgreSQL 17, Redis 7, RabbitMQ 4)

## 1. NON-NEGOTIABLE RULES (From instructions.txt + Overlay)

Every agent session must enforce these without exception:

| Rule | Detail |
| :--- | :--- |
| **TDD mandatory** | Red → Green → Refactor. Write ALL tests BEFORE implementation. Run to confirm Red. Then implement to Green. |
| **Constructor injection only** | `@RequiredArgsConstructor` + `private final`. ZERO `@Autowired` anywhere in production code. |
| **Instant everywhere** | Use `java.time.Instant` for all timestamps. NEVER use `LocalDateTime`. |
| **@Transactional pattern** | `@Transactional(readOnly = true)` at class level. Override with `@Transactional` on write methods only. |
| **DTO boundaries** | Never expose JPA entities through API. Always map to/from DTOs at controller boundary. |
| **Thin controllers** | Controllers route, validate input, call service, return `ResponseEntity<ApiResponse<T>>`. No business logic. |
| **SLF4J logging** | Use parameterized `logger.info("... {}", var)`. Never string concatenation in log calls. |
| **Correlation ID (Fix CC-1)** | All log statements in service classes should propagate MDC correlation ID from `CorrelationIdFilter`. Currently applied to Day 1/2 files. **DEFERRED for Day 3 new services — apply in Day 7 cleanup.** |
| **BusinessConstants (Fix CC-2)** | ZERO magic numbers. All constants in `com.ticketing.common.util.BusinessConstants`. |
| **No method > 20 lines** | Refactor long methods into private helpers. |
| **Flyway migrations are IMMUTABLE** | Never edit a migration file after it has been run. Create a new Vn__ file for schema changes. |
| **Package structure** | Strict domain packages under `com.ticketing`. No cross-domain direct dependency (use DTOs). |
| **ApiResponse wrapper** | All endpoints return `ResponseEntity<ApiResponse<T>>`. Use `ApiResponse.success(data)` and `ApiResponse.failure(msg)`. |

---

## 2. PROJECT STRUCTURE — Overview

### Main Source (`src/main/java/com/ticketing/`)

- `booking/`: Handles Booking domain, state machine logic (`BookingState`, `BookingEvent`, `CheckInGuard`), and ticket generation.
- `common/`: Security (JWT filters), `GlobalExceptionHandler`, DTO wrappers, Redis/RabbitMQ configs, `DistributedLockService`, and `BusinessConstants`.
- `event/`: Domain logic for Events, Venues, Categories, and Search. Includes `EventSearchService`.
- `inventory/`: High-performance seat reservation via Redis and Lua scripts (`InventoryService`).
- `payment/`: Stripe webhooks and checkout payment processing.
- `user/`: Authentication, JWT generation, and User entity management.

### Test Source (`src/test/java/com/ticketing/`)

- Full test coverage for `event`, `user`, and `inventory` packages.
- `TestSecurityConfig.java` in `common/config/` is strictly required for any `@WebMvcTest`.
- `TestcontainersConfiguration.java` provisions PostgreSQL and Redis.

### Database Migrations (`src/main/resources/db/migration/`)

| File | Contents | Status |
| :--- | :--- | :--- |
| V1__create_users_table.sql | users table with ENUM role type | IMMUTABLE |
| V2__create_venues_and_categories.sql | venues + categories tables | IMMUTABLE |
| V3__create_events_table.sql | events table | IMMUTABLE |
| V4__create_ticket_tiers.sql | ticket_tiers table | IMMUTABLE |
| V5__create_bookings_and_tickets.sql | bookings + tickets tables | IMMUTABLE |
| V6__create_payments_and_refunds.sql | payments + refunds tables | IMMUTABLE |
| V7__create_indexes.sql | Performance indexes | IMMUTABLE |
| V8__add_event_features.sql | waitlist_enabled, dynamic_pricing_enabled on events | IMMUTABLE |
| V9__seed_data.sql | 5 categories (Music, Sports, Comedy, Theater, Festival) + 3 venues | IMMUTABLE |
| V10__add_ticket_tier_version.sql | @Version column on ticket_tiers for optimistic locking | IMMUTABLE |
| V11__add_waitlist_and_refund_reason.sql | waitlist_entries table + bookings.refund_denial_reason | IMMUTABLE |

**NEXT MIGRATION MUST BE: V12__...**

---

## 3. SECURITY ARCHITECTURE

### Production Security (`SecurityConfig.java`)

- `@EnableWebSecurity` + `@EnableMethodSecurity` on `SecurityConfig`
- `JwtFilter` registered before `UsernamePasswordAuthenticationFilter`
- Public GET rules: `/api/auth/**`, `/api/events`, `/api/events/**`, `/api/search/events`, `/api/venues`, `/api/venues/**`, `/api/categories`, `/api/categories/**`
- All other requests: `.anyRequest().authenticated()`
- Method-level security: `@PreAuthorize("hasRole('ADMIN')")` on Venue/Category write ops
- Method-level security: `@PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")` on Event write ops

### Test Security (`TestSecurityConfig.java`) — CRITICAL FOR ALL NEW `@WebMvcTest` SLICES

**Location:** `src/test/java/com/ticketing/common/config/TestSecurityConfig.java`

**How to use in every `@WebMvcTest` class:**

```java
@WebMvcTest(controllers = YourController.class)
@Import(TestSecurityConfig.class)        // ← MANDATORY
class YourControllerTest {

    @MockitoBean
    private YourService yourService;

    @MockitoBean
    private com.ticketing.common.security.JwtService jwtService;  // ← MANDATORY — satisfies JwtFilter wiring

    // NO @AutoConfigureMockMvc(addFilters = false) — filters must be ENABLED for @PreAuthorize to fire
    // NO @MockitoBean UserDetailsService — TestSecurityConfig provides it
}
```

**Why `@MockitoBean JwtService` is required:**
`JwtFilter` is a `@Component` and is picked up by `@WebMvcTest`. It injects `JwtService` via constructor. Even though `TestSecurityConfig` does NOT add `JwtFilter` to its filter chain, Spring still tries to create the `JwtFilter` bean for the application context. Without a `JwtService` mock, this fails with `UnsatisfiedDependencyException`.

**Why NOT `addFilters = false`:**
`addFilters = false` disables the entire Servlet filter chain AND the `@EnableMethodSecurity` AOP proxy. `@PreAuthorize` annotations are silently ignored — the tests appear to pass but provide zero security coverage.

**Test users available via `@WithMockUser`:**

- `@WithMockUser(roles = "ADMIN")` — has ADMIN authority
- `@WithMockUser(roles = "ORGANIZER")` — has ORGANIZER authority
- No annotation = unauthenticated → returns 401 (configured via `HttpStatusEntryPoint`)

---

## 4. CURRENT OVERLAY FIX STATUS

| Fix ID | Severity | Description | Status | Applied Where |
| :--- | :--- | :--- | :--- | :--- |
| **1.1** | Use `Instant` (UTC) instead of `LocalDateTime` | DB / Models | ✅ Applied | ✅ Verified |
| **1.2** | PostgreSQL ENUM for `user_role` | DB / Models | ✅ Applied | ✅ Verified |
| **1.3** | Soft delete column `deleted_at` on bookings | DB / Models | ✅ Applied | ✅ Verified |
| **2.1** | `@Transactional(readOnly = true)` on read methods | Services | ✅ Applied | ✅ Verified |
| **2.2** | Constructor injection ONLY (No `@Autowired`) | All Classes | ✅ Applied | ✅ Verified |
| **5.1** | Lua script floor guard for `reserveSeat()` | Inventory | ✅ Applied | ✅ Verified |
| **5.2** | Redis Startup Health Indicator (`InventoryWarmupHealthIndicator`) | Infra | ✅ Applied | ✅ Verified |
| **7.1** | `@Version` for optimistic locking | Models | ✅ Applied | ✅ Verified |
| **7.2** | Docker Compose `service_healthy` conditions | Infra | ✅ Applied | ✅ Verified |
| **CC-1** | `X-Correlation-ID` Propagation | Config | ✅ Applied | ✅ Verified |
| **CC-2** | Business Constants (No magic numbers) | Util | ✅ Applied | ✅ Verified |
| **A.1** | BUG-01: Hollow warmup in InventoryService | Inventory | ✅ Applied | ✅ Verified |
| **A.2** | BUG-04: `maxPerBooking` magic number 10 removed | Models | ✅ Applied | ✅ Verified |
| **A.3** | BUG-02: `CANCELLED` state added, `AVAILABLE` removed | Models | ✅ Applied | ✅ Verified |
| **A.4** | BUG-03: `BookingRepository` required methods added | DB / Models | ✅ Applied | ✅ Verified |
| **8.1** | Double-Check availability inside Lock (TOCTOU guard) | Booking | ✅ Applied | ✅ Verified |
| **8.2** | `CHECK_IN` dual-guard (HTTP + State Machine) | Booking | ✅ Applied | ✅ Verified |
| **8.3** | `@Scheduled` Expiry Job distributed lock | Booking | ✅ Applied | ✅ Verified |
| **9.1** | Webhook HTTP 200 *after* commit (NOT `@Transactional` controller) | Payment | ✅ Applied | ✅ Verified |
| **9.2** | Webhook Idempotency with Concurrent Delivery Guard (`DataIntegrityViolationException`) | Payment | ✅ Applied | ✅ Verified |
| **10.1** | Add `DENY_REFUND` Notification Action | Notifications | ✅ Applied | ✅ Verified |
| **10.2** | RabbitMQ integration active (ticket/email queues) | Notifications | ✅ Applied | ✅ Verified |
| **11.1** | Pricing Engine + Waitlist Service | Pricing | ✅ Applied | ✅ Verified |
| **11.2** | Clarify `RELEASE` event caller / Terminal States | Booking | ✅ Applied | ✅ Verified |
| **12.1** | Add `refund_denial_reason` field for transparency | Booking | ✅ Applied | ✅ Verified |
| **16.1** | Test `reserveSeat()` Lua Script explicitly (concurrency test) | Tests | ⏳ Pending | - |

---

## 5. DAY-BY-DAY COMPLETION STATE

| Day | Theme | Status | Tests |
| :--- | :--- | :--- | :--- |
| 1–7 | Week 1: Core Domain + Inventory + Cleanup | ✅ | 68/68 |
| 8 | Booking State Machine | ✅ | 73/73 |
| 9 | Stripe Checkout + Webhook | ✅ | 80/80 |
| 10 | RabbitMQ Consumers + Notifications | ✅ | 83/83 |
| 11 | Pricing Engine + Waitlist | ✅ | 88/88 |
| 12 | Refund Logic + Concurrency Polish | ⬜ | — |

---

## 6. GLOBALEXCEPTIONHANDLER — CURRENT HANDLERS

All exceptions must flow through `GlobalExceptionHandler`. Current mapping:

| Exception | HTTP Status | Handler Method |
| :--- | :--- | :--- |
| `EntityNotFoundException` | 404 | `handleEntityNotFound` |
| `AccessDeniedException` | 403 | `handleAccessDenied` |
| `ValidationException` | 400 | `handleValidation` |
| `MethodArgumentNotValidException` | 400 | `handleMethodArgumentNotValid` |
| `ConstraintViolationException` | 400 | `handleConstraintViolation` (added Day 3) |
| `AuthenticationException` | 401 | `handleAuthentication` |

When adding new services, do NOT add new exception types without adding a handler here first.

---

## 7. API ENDPOINT MAP (Complete as of Day 3)

| Method | Endpoint | Auth Required | Role |
| :--- | :--- | :--- | :--- |
| POST | /api/auth/register | No | — |
| POST | /api/auth/login | No | — |
| GET | /api/events | No | — |
| GET | /api/events/{id} | No | — |
| POST | /api/events | Yes | ORGANIZER or ADMIN |
| PUT | /api/events/{id} | Yes | ORGANIZER or ADMIN |
| DELETE | /api/events/{id} | Yes | ORGANIZER or ADMIN |
| POST | /api/events/{id}/publish | Yes | ORGANIZER or ADMIN |
| GET | /api/search/events | No | — |
| GET | /api/venues | No | — |
| GET | /api/venues/{id} | No | — |
| POST | /api/venues | Yes | **ADMIN only** |
| PUT | /api/venues/{id} | Yes | **ADMIN only** |
| DELETE | /api/venues/{id} | Yes | **ADMIN only** |
| GET | /api/categories | No | — |
| GET | /api/categories/{id} | No | — |
| POST | /api/categories | Yes | **ADMIN only** |
| PUT | /api/categories/{id} | Yes | **ADMIN only** |
| DELETE | /api/categories/{id} | Yes | **ADMIN only** |

---

## 8. KNOWN BLOCKERS AND ENVIRONMENT NOTES

| Blocker | Impact | Resolution |
| :--- | :--- | :--- |
| Docker Desktop not running | `TicketingPlatformApplicationTests` fails (2 errors) | Start Docker Desktop before running `./mvnw verify`. These are integration tests requiring PostgreSQL + Redis containers. All unit tests pass without Docker. |
| No Testcontainers for Day 3 service-level integration tests | EventSearchService filter behavior not verified against real DB | Deferred to Day 6 integration test day |
| Fix CC-1 not applied to Day 3 new services | VenueService, CategoryService, EventSearchService log without correlation ID | Apply in Day 7 cleanup — not a functional blocker |

---

## 9. DEPLOYMENT GUIDE — FULL CLARIFICATION

This repository has two deployable parts:

- Backend: Spring Boot application under the repository root.
- Frontend: Next.js application under `frontend/`.

### 9.1 Local Full-Stack Start

1. Start infrastructure from the repository root:
   - `docker-compose up -d`

2. Start the backend:
   - `./mvnw spring-boot:run`

3. Set the frontend API URL:
   - `frontend/.env.local` must contain `NEXT_PUBLIC_API_URL=http://localhost:8080`

4. Start the frontend:
   - `cd frontend && npm run dev`

### 9.2 Production Build Order

1. Build and deploy the backend first.
2. Set `NEXT_PUBLIC_API_URL` to the public backend URL before building the frontend.
3. Build the backend with:
   - `./mvnw -q -DskipTests compile`
4. Build the frontend with:
   - `cd frontend && npm run build`

### 9.3 Required Runtime Services

- PostgreSQL 17
- Redis 7
- RabbitMQ 4-management
- Spring Boot backend service
- Next.js frontend service

### 9.4 Deployment Rules

- Never hardcode a backend host in the frontend source code.
- Keep all API access routed through `src/lib/api.ts`.
- Keep `.env.local` out of version control.
- Commit only `frontend/.env.example` so the required env var is obvious.
- Treat the Windows SWC warning as a local build environment issue if webpack fallback succeeds.

### 9.5 Operational Order

- Database and infrastructure must be up before backend start.
- Backend must be reachable before frontend release or preview deployment.
- Frontend build uses `NEXT_PUBLIC_API_URL` at build time, so the env value must be correct before `npm run build`.

---

## 10. DAY 4 FRONTEND GAP CLOSURE

### Implemented After Initial Day 4 Core

- Shared search helper added: `frontend/src/lib/search.ts`.
- Navbar search now routes to `/search?q=...`.
- Home page CTA routes to shareable `/search` URLs with query params.
- Search page now has an `Apply Filters` button that updates the URL.
- Event details page added at `frontend/src/app/events/[id]/page.tsx`.
- Basic helper tests added for search URL and filter logic.

### Validation Status

- `frontend` build: PASS (`npm run build`)
- `frontend` helper tests: PASS (`npm test`)
- Routes present: `/`, `/search`, `/events/[id]`

---

## 9. ANTI-HALLUCINATION CONSTRAINTS (READ BEFORE EVERY CODE CHANGE)

1. **Do NOT modify any Flyway migration file** — create V11__ or higher instead.
2. **Do NOT change `TestSecurityConfig.java`** unless a new test scenario genuinely requires it.
3. **Do NOT add `addFilters = false`** to any `@WebMvcTest` class — this disables `@PreAuthorize`.
4. **Do NOT use `@Autowired`** in any production class — constructor injection only.
5. **Do NOT introduce `LocalDateTime`** — use `Instant` everywhere.
6. **Do NOT modify `GlobalExceptionHandler`** without adding a corresponding test for the new handler.
7. **Do NOT create new beans that conflict with `TestSecurityConfig`** — check bean names first.
8. **Do NOT mark a day complete in PROGRESS.md** unless `./mvnw test` (excluding Docker tests) shows zero failures.
9. **Do NOT change existing public method signatures** in services without updating all call sites and tests.
10. **Do NOT skip the TDD gate** — if a test is green before implementation, the test is wrong.

---

## 10. NEXT SESSION START — DAY 12

**Current Branch:** `day-12-refund-concurrency`

**First task:** Refund Logic + Concurrency Polish

- Review Day 12 plan for Refund logic and concurrency testing.
- Ensure Day 11 waitlist edge cases do not interfere with refund state transitions.
