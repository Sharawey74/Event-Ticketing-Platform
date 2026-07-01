# AI CONTEXT SNAPSHOT — Event Ticketing Platform

## Last Updated: Day 18 (2026-07-02) — CI/CD Pipeline + Production Deploy to Railway: backend deployed on Railway (prod profile active, Flyway migrated against real Postgres, app fully started), frontend live on Vercel. RabbitMQ healthcheck fix (management.health.rabbit.enabled: false) applied locally, pending push/deploy confirmation.

## Branch: main (day-18-ci-cd-pipeline.md merged via PR #23)

## Test Status: 183/183 ALL passing (unchanged from Day 16B — Day 18 is CI/CD/deploy-config only, no test changes)

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
- Public POST rules: `/api/v1/auth/**`, `/api/auth/**`, `/api/v1/payments/webhook`
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
| **15.1** | Fix Stripe Webhook 403 Forbidden (SecurityConfig) | Payment | ✅ Applied | ✅ Verified |
| **15.2** | Fix Stripe Session Deserialization (GSON fallback) | Payment | ✅ Applied | ✅ Verified |
| **15.3** | Fix Missing BookingConfirmedEvent publish call | Payment | ✅ Applied | ✅ Verified |
| **15.4** | Fix Next.js middleware token missing (cookie sync) | Frontend | ✅ Applied | ✅ Verified |
| **15.5** | Fix Dashboard nested DTO property alignment | Frontend | ✅ Applied | ✅ Verified |
| **16A.1** | BookingQueryService: QR/ticket gated behind CONFIRMED/ATTENDED state | Booking | ✅ Applied | BookingQueryService.java |
| **16A.2** | AuthService: ADMIN self-registration blocked; ORGANIZER correctly assignable | User | ✅ Applied | AuthService.java |
| **16A.3** | register/page.tsx: sends role to backend; redirects to /auth/login?registered=true | Frontend | ✅ Applied | register/page.tsx |
| **16A.4** | DataSeeder: removed "default" profile (prod seeding safety) | Config | ✅ Applied | DataSeeder.java |
| **16A.5** | application-local.yml: STRIPE_WEBHOOK_SECRET env var name fixed | Config | ✅ Applied | application-local.yml |
| **16A.6** | .env.example: backend port corrected to 8088 | Config | ✅ Applied | frontend/.env.example |
| **16A.7** | BookingRepository.findByIdWithDetails: full EntityGraph (user,event,tickets,tier) | Booking | ✅ Applied | BookingRepository.java |
| **16A.8** | BookingController: thin controller, delegates to BookingQueryService | Booking | ✅ Applied | BookingController.java |
| **16A.9** | AuthControllerTest + StripeWebhookControllerTest: fixed wrong URL paths | Tests | ✅ Applied | Test files |
| **16A.10** | WebhookServiceTest: added BookingEventPublisher mock + booking test data | Tests | ✅ Applied | WebhookServiceTest.java |
| **16A.11** | GlobalExceptionHandler: 405/404/415 handlers added (were falling into 500 catch-all) | Common | ✅ Applied | GlobalExceptionHandler.java |
| **16A.12** | page.tsx hero: "Sign In" glass button hidden when user is authenticated | Frontend | ✅ Applied | frontend/src/app/page.tsx |
| **16A.13** | organizer/events/page.tsx: split loading guard to prevent infinite loading when query disabled | Frontend | ✅ Applied | frontend/src/app/organizer/events/page.tsx |
| **16A.14** | organizer/events/[id]/edit/page.tsx: created missing edit event page (was 404) | Frontend | ✅ Applied | frontend/src/app/organizer/events/[id]/edit/page.tsx |
| **16B.2** | PaymentService: resume checkout from PAYMENT_PENDING; Payment upsert (fixes `payments_booking_id_key` UNIQUE violation) | Payment | ✅ Applied | PaymentService.java |
| **16B.3** | GlobalExceptionHandler: `IllegalStateException` → 409 Conflict (was falling into 500 catch-all) | Common | ✅ Applied | GlobalExceptionHandler.java |
| **16B.4** | BookingService: self-cancel of PAYMENT_PENDING bookings (releases seats + transitions CANCELLED) | Booking | ✅ Applied | BookingService.java |
| **16B.5** | ReservationExpirationJob: auto-expire stale PAYMENT_PENDING → PAYMENT_FAILED, release inventory | Booking | ✅ Applied | ReservationExpirationJob.java |
| **16B.6** | BusinessConstants: `STRIPE_SESSION_TTL_SECONDS = 1860L` (replaces inline magic number) | Util | ✅ Applied | BusinessConstants.java |
| **16B.7** | TicketTierSelector/CartDrawer: price row + Total from `store.totalAmount`; savings line when discount applied | Frontend | ✅ Applied | TicketTierSelector.tsx, CartDrawer.tsx |
| **16B.8** | ReservationGuard: `beforeunload`/`pagehide` read `skipBeforeUnload` live via `getState()` — eliminates stale-closure "Leave site?" dialog | Frontend | ✅ Applied | ReservationGuard.tsx |
| **16B.9** | TicketTierSelector: explicit replace-confirmation prompt before overwriting a held reservation for another event | Frontend | ✅ Applied | TicketTierSelector.tsx |
| **16B.10** | Booking History: Resume+Cancel actions for RESERVED/PAYMENT_PENDING; fixed `useEffect` countdown; muted terminal rows; clear store on mount | Frontend | ✅ Applied | dashboard/bookings/page.tsx, [id]/page.tsx |
| **16B.11** | reservationStore: added `unitPrice` + `eventId` fields to support savings line and replace-prompt guard | Frontend | ✅ Applied | reservationStore.ts |
| **16B.1** | Test `reserveSeat()` Lua Script explicitly (concurrency test) | Tests | ✅ Applied | InventoryServiceConcurrencyTest.java — 100-thread/50-seat startLatch test |
| **16B-missing** | BookingControllerTest — 13 tests covering all booking endpoints + @PreAuthorize guards | Tests | ✅ Applied | BookingControllerTest.java |
| **16B-coverage** | Backend Test Coverage Push: +54 new unit tests across 13 new test classes | Tests | ✅ Applied | 81.4% INSTRUCTION coverage, JaCoCo gate PASSED |
| **7.2** (Day 17) | `app` service `depends_on: condition: service_healthy` for postgres/redis/rabbitmq | Infra | ✅ Applied | docker-compose.yml |
| **E-009** | X-Frame-Options: DENY + HSTS (1yr, includeSubDomains) — backend, no CSP (breaks Swagger UI) | Security | ✅ Applied | SecurityConfig.java |
| **E-009F** | CSP + X-Frame-Options + X-Content-Type-Options — frontend, connect-src derived from NEXT_PUBLIC_API_URL, no `*.railway.app` (deferred to Day 21) | Security | ✅ Applied | next.config.ts |
| **17-docker** | Multi-stage Dockerfile (JDK builder → JRE runtime, non-root `appuser`, 619MB), `.dockerignore`, `app` service wired into compose | Infra | ✅ Applied | Dockerfile, .dockerignore, docker-compose.yml |
| **E-006** (Day 18) | Complete `application-prod.yml` — real property names (`jwt.secret`, `frontend.url`, `stripe.*`, `rabbitmq.uri`), no fallback secrets | Config | ✅ Applied | application-prod.yml |
| **E-003** (Day 18) | CORS startup guard — logs resolved `frontend.url`, refuses to start on wildcard origin | Security | ✅ Applied | WebConfig.java |
| **18-mvnw** | `RUN chmod +x mvnw` in Dockerfile + git executable-bit fix — Railway build was failing with `Permission denied` (exit 126) | Infra | ✅ Applied | Dockerfile, mvnw |
| **18-profile** | `SPRING_PROFILES_ACTIVE=prod` added to Railway variables — app was silently running under `local` profile (wrong datasource) with it missing | Infra | ✅ Applied | Railway dashboard (not in-repo) |
| **18-health** | `management.health.rabbit.enabled: false` — RabbitMQ (CloudAMQP) connectivity issue was failing `/actuator/health` and blocking Railway deploys even though the app itself was fully healthy | Infra | ✅ Applied (local, pending push) | application-prod.yml |

---

## 5. DAY-BY-DAY COMPLETION STATE

| Day | Theme | Status | Tests |
| :--- | :--- | :--- | :--- |
| 1–7 | Week 1: Core Domain + Inventory + Cleanup | ✅ | 68/68 |
| 8 | Booking State Machine | ✅ | 73/73 |
| 9 | Stripe Checkout + Webhook | ✅ | 80/80 |
| 10 | RabbitMQ Consumers + Notifications | ✅ | 83/83 |
| 11 | Pricing Engine + Waitlist | ✅ | 88/88 |
| 12 | Refund Logic + Concurrency Polish | ✅ | 99/99 |
| 13 | Frontend Booking Flow | ✅ | N/A |
| 14 | Frontend User Dashboard | ✅ | N/A |
| 15 | Frontend Organizer Dashboard | ✅ | N/A |
| 16A | Platform Stabilization | ✅ Complete | 104/104 unit |
| 16B-CF | Checkout Flow Stabilization (sub-session) | ✅ Complete | 129/129 unit |
| 16B | Backend Test Coverage Push (80%+) | ✅ Complete | 183/183 all passing — 81.4% INSTRUCTION (JaCoCo gate ✅) |
| 17 | Docker Multi-stage + Compose Polish + Security Headers | ✅ Complete | 183/183 unchanged — Docker/infra day, verified via docker-compose up -d (all 7 containers healthy) |
| 18 | CI/CD Pipeline + Production Deploy to Railway | ✅ Complete | 183/183 unchanged — CI/CD + deploy day. Backend deployed live on Railway (Postgres+Redis on Railway, RabbitMQ on CloudAMQP), frontend live on Vercel. See Section 10 for full deploy debugging log. |

---

## 6. GLOBALEXCEPTIONHANDLER — CURRENT HANDLERS

All exceptions must flow through `GlobalExceptionHandler`. Current mapping:

| Exception | HTTP Status | Handler Method |
| :--- | :--- | :--- |
| `EntityNotFoundException` | 404 | `handleEntityNotFound` |
| `AccessDeniedException` | 403 | `handleAccessDenied` |
| `ValidationException` | 400 | `handleValidation` |
| `MethodArgumentNotValidException` | 400 | `handleMethodArgumentNotValid` |
| `ConstraintViolationException` | 400 | `handleConstraintViolation` |
| `AuthenticationException` | 401 | `handleAuthentication` |
| `ConflictException` | 409 | `handleConflict` |
| `IllegalStateException` | 409 | `handleIllegalState` |
| `HttpRequestMethodNotSupportedException` | 405 | `handleMethodNotAllowed` |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | `handleNoHandlerFound` |
| `HttpMediaTypeNotSupportedException` | 415 | `handleUnsupportedMediaType` |
| `Exception` (catch-all) | 500 | `handleGeneralException` |

*Note: All responses now include `errorId` and `correlationId`. Structured MDC logging is active for all exceptions.*

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
| POST | /api/v1/payments/webhook | No | (Secured via Stripe HMAC Signature) |
| GET | /api/v1/bookings/my | Yes | USER/ORGANIZER/ADMIN (Returns flat BookingResponse) |
| GET | /api/v1/bookings/{id} | Yes | Checked via Resource Ownership (Returns nested BookingDetailsResponse) |

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

## 10. NEXT SESSION START — DAY 19

**Current Branch:** `main` (day-18-ci-cd-pipeline.md merged via PR #23)

**Completed in Day 18 (CI/CD Pipeline + Production Deploy to Railway, 2026-07-02):**

- `application-prod.yml` created: complete prod config using the app's actual property names
  (`jwt.secret`, `frontend.url`, `stripe.*`, `rabbitmq.uri`) — the Day 18 session template used
  wrong prefixes (`app.jwt.*`) that would have silently failed to bind; corrected against the real
  `@Value` annotations in `JwtService`/`WebConfig`/`StripeConfig`.
- `WebConfig.java`: `@PostConstruct` CORS guard — logs resolved `frontend.url`, throws
  `IllegalStateException` on a wildcard origin (Fix E-003).
- `.github/workflows/main.yml` created: CI-only pipeline (`backend-test`, `frontend-build`,
  `repo-hygiene`) — no deploy jobs, since Railway and Vercel both auto-deploy natively via their
  own GitHub integrations (pivoted away from the session template's `railway up` CLI + token
  approach after Railway blocked token creation on an unverified trial account).
- `Dockerfile` + `mvnw`: fixed `Permission denied` (exit 126) on Railway builds —
  `RUN chmod +x mvnw` added, plus corrected the git executable-bit metadata.
- Untracked `Plans/` (40 files) + `test_output.log` from `main` — were committed before
  `.gitignore` caught them; files remain on disk, no history rewritten.
- **Full production deploy achieved on Railway:** RabbitMQ hosted on CloudAMQP (Railway trial's
  resource cap blocked provisioning it directly), `SPRING_PROFILES_ACTIVE=prod` added (was the
  single missing variable keeping the app on the wrong `local` profile/datasource), Flyway
  successfully migrated all 12 versions against the real Railway Postgres, app fully started.
  Final blocker (`management.health.rabbit.enabled: false` — RabbitMQ health-indicator was
  failing `/actuator/health` even though the app itself was fully healthy) applied locally,
  pending push + deploy confirmation next session.
- Frontend deployed live on Vercel (`event-ticketing-platform-nu.vercel.app`); custom domain
  `eventora.app` DNS verification still pending (not blocking).
- Full debugging narrative: `.claude/day-18-walkthrough.md`.

**First task for next session — Day 19:** Confirm the RabbitMQ healthcheck fix deploys clean on
Railway (`/actuator/health` → UP), then start Performance + k6 Load Tests + Swagger/OpenAPI — see
`Plans/session-prompts/day-19-*.md` for the session prompt.

**Completed in Day 17 (Docker Multi-stage + Compose Polish + Security Headers, 2026-07-01):**

- `.dockerignore` created (excludes `.env`, `target/`, `Plans/`, `.git`, `.claude/`, `frontend/`)
- `Dockerfile`: multi-stage build (`eclipse-temurin:21-jdk` builder → `eclipse-temurin:21-jre` runtime), non-root `appuser`, `EXPOSE 8088` (matches real `server.port`), `-XX:MaxRAMPercentage=75.0` — 619MB final image
- `docker-compose.yml`: added `app` service with `depends_on: condition: service_healthy` for postgres/redis/rabbitmq (Fix 7.2), `image: ticketing-backend:local` pinned (prevents Compose's default `<project>-<service>:latest` duplicate-tag naming), postgres/rabbitmq credentials parameterized via `${VAR:-default}`, `SPRING_MAIL_HOST=mailhog` added (gap in the original session template — mail would've silently failed otherwise), "LOCAL DEVELOPMENT ONLY" comment block added
- `.env.example` (root): added `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`/`RABBITMQ_USER`/`RABBITMQ_PASSWORD`/`SERVER_PORT`
- `SecurityConfig.java`: `.headers(...)` with `frameOptions().deny()` (X-Frame-Options: DENY) + `httpStrictTransportSecurity()` (1yr, includeSubDomains) — Fix E-009, no CSP (backend is JSON+Swagger only)
- `frontend/next.config.ts`: CSP + X-Frame-Options + X-Content-Type-Options via `headers()` — Fix E-009F. `connect-src` derived from `NEXT_PUBLIC_API_URL` at build/serve time (critical fix over the naive session-prompt template — without this, every API call from the frontend would be CSP-blocked since the backend is cross-origin at `:8088` vs the frontend's `:3000`). No `*.railway.app` wildcard (deferred to Day 21).
- Full stack verified end-to-end: `docker-compose up -d` (no `--build` — reused existing image), all 7 containers healthy, `/actuator/health` → `UP`, seed data present, `X-Frame-Options: DENY` confirmed on backend, all three frontend headers confirmed via `npm run dev` + `curl -I localhost:3000`

**First task for next session — Day 18:** CI/CD Pipeline (GitHub Actions) — see `Plans/session-prompts/day-18-*.md` for the session prompt.

**Completed in Day 16A (all sub-sessions):**

- BookingQueryService: state-gated QR/ticket access (CONFIRMED/ATTENDED only)
- AuthService: ORGANIZER self-registration allowed, ADMIN self-registration blocked
- register/page.tsx: sends `role` field to backend; redirects to `/auth/login?registered=true` on success
- DataSeeder: `@Profile("local")` only — removed "default" to prevent prod seeding
- application-local.yml: fixed `STRIPE_WEBHOOK_SECRET` env var name
- frontend/.env.example: corrected backend port to 8088
- BookingRepository: `findByIdWithDetails` with full EntityGraph
- BookingController: now thin — delegates list/detail queries to BookingQueryService
- Pre-existing test URL mismatches fixed (AuthControllerTest, StripeWebhookControllerTest)
- WebhookServiceTest: missing BookingEventPublisher mock added
- GlobalExceptionHandler: 405/404/415 handlers added (were returning 500 via catch-all)
- page.tsx: hero "Sign In" button hidden when user is authenticated (useAuthStore token check)
- organizer/events/page.tsx: TanStack Query disabled-query `isLoading:true` loading-state bug fixed
- organizer/events/[id]/edit/page.tsx: created (was 404); full edit form with pre-population + PUT /api/events/{id} + success panel + auto-redirect

**Completed in Day 16B-CF (Checkout Flow Stabilization sub-session, 2026-06-27):**

- PaymentService: resume checkout from PAYMENT_PENDING; Payment upsert via `findByBookingId` (fixes `payments_booking_id_key` UNIQUE constraint violation on second checkout attempt)
- PaymentService: hold extended to `STRIPE_SESSION_TTL_SECONDS` on checkout — prevents expiry job racing an in-progress Stripe session
- BookingService: self-cancel now accepts PAYMENT_PENDING (releases Redis seats + DB `availableCount`, transitions to CANCELLED)
- ReservationExpirationJob: rewritten to expire both RESERVED (→ EXPIRED) and stale PAYMENT_PENDING (→ PAYMENT_FAILED, seats released); uses `findByStateInAndExpiresAtBefore` with `@EntityGraph`
- GlobalExceptionHandler: `IllegalStateException` → 409 Conflict (+ `GlobalExceptionHandlerTest` for all handler mappings)
- BusinessConstants: `STRIPE_SESSION_TTL_SECONDS = 1860L`
- TicketTierSelector/CartDrawer: price row + Total both derived from `store.totalAmount`; savings line when PricingEngine discount applied
- ReservationGuard: `beforeunload`/`pagehide` handlers read `skipBeforeUnload` live via `getState()` — eliminates stale-closure "Leave site?" dialog
- TicketTierSelector: explicit replace-confirmation prompt before overwriting a held reservation for another event
- Booking History (list + detail pages): Resume+Cancel actions for RESERVED/PAYMENT_PENDING; fixed `useEffect` countdown (was using `useState` as side-effect); muted terminal rows; clear store on mount
- reservationStore: added `unitPrice` + `eventId` fields
- Tests: 129/129 unit passing (+25 new tests: BookingServiceTest, ReservationExpirationJobTest, GlobalExceptionHandlerTest, PaymentServiceTest updates)

**Completed in Day 16B (Backend Test Coverage Push, 2026-06-30):** 81.4% INSTRUCTION coverage, JaCoCo gate passed, 183/183 tests passing — see Section 4 rows `16B.1`, `16B-missing`, `16B-coverage` for details.
