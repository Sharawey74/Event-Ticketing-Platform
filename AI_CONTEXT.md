# AI CONTEXT SNAPSHOT — Event Ticketing Platform
## Last Updated: Day 3 & 4 Audit Remediation Completion (2026-04-24)
## Branch: day-4-nextjs-frontend-init-home
## Test Status: 56/56 passing (2 Docker/Testcontainers errors are pre-existing, require Docker Desktop)

---

## 1. NON-NEGOTIABLE RULES (From instructions.txt + Overlay)

Every agent session must enforce these without exception:

| Rule | Detail |
|------|--------|
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

## 2. PROJECT STRUCTURE — Complete File Map

### Main Source (`src/main/java/com/ticketing/`)

```
booking/
  model/
    Booking.java            — JPA entity, includes @Version for optimistic locking
    BookingState.java       — Enum: AVAILABLE, RESERVED, PAYMENT_PENDING, CONFIRMED, etc.
    Ticket.java             — JPA entity
    TicketTier.java         — JPA entity, price/capacity per tier
  repository/
    TicketTierRepository.java
  service/
    TicketTierService.java  — getAvailableCount(Long tierId) [COMPLETE, tested]

common/
  config/
    WebConfig.java          — CORS configuration
  dto/
    ApiResponse.java        — Generic response wrapper: success(data), failure(msg)
    PageResponse.java       — Paginated response wrapper
  exception/
    GlobalExceptionHandler.java  — @ControllerAdvice handling all exceptions [COMPLETE]
  filter/
    CorrelationIdFilter.java     — Sets MDC X-Correlation-ID on every request
  security/
    JwtFilter.java          — OncePerRequestFilter: extracts + validates JWT from Authorization header
    JwtService.java         — generateToken, extractUsername, isTokenValid
    SecurityConfig.java     — @EnableMethodSecurity, SecurityFilterChain, public GET rules
  util/
    BusinessConstants.java  — All magic-number-free constants

event/
  controller/
    CategoryController.java — CRUD: POST/GET/PUT/DELETE. Write ops: @PreAuthorize("hasRole('ADMIN')")
    EventController.java    — CRUD: POST/GET/PUT/DELETE/PUBLISH. @PreAuthorize ORGANIZER/ADMIN
    EventSearchController.java — GET /api/search/events. @Validated + @Size(max=100) on q and city
    VenueController.java    — CRUD: POST/GET/PUT/DELETE. Write ops: @PreAuthorize("hasRole('ADMIN')")
  dto/
    CategoryResponse.java
    CreateCategoryRequest.java
    CreateEventRequest.java
    CreateVenueRequest.java
    EventFilterRequest.java
    EventResponse.java
    UpdateCategoryRequest.java
    UpdateEventRequest.java
    UpdateVenueRequest.java
    VenueResponse.java
  model/
    Category.java
    Event.java              — includes dynamicPricingEnabled, waitlistEnabled (added V8)
    EventStatus.java        — DRAFT, PUBLISHED, SALES_OPEN, SALES_CLOSED, COMPLETED, ARCHIVED
    Venue.java
  repository/
    CategoryRepository.java
    EventRepository.java    — searchPublishedEvents with @EntityGraph (prevents N+1)
    VenueRepository.java
  service/
    CategoryService.java    — Full CRUD, @Transactional(readOnly=true) pattern [COMPLETE]
    EventSearchService.java — searchEvents(query, categoryId, city, pageable) [COMPLETE]
    EventService.java       — Full CRUD + publish [COMPLETE]
    VenueService.java       — Full CRUD, @Transactional(readOnly=true) pattern [COMPLETE]

payment/
  model/
    Payment.java
    PaymentStatus.java
    Refund.java
    RefundStatus.java

user/
  controller/
    AuthController.java     — POST /api/auth/register, POST /api/auth/login
  dto/
    AuthResponse.java, LoginRequest.java, RegisterRequest.java
  model/
    Role.java               — Enum: USER, ORGANIZER, ADMIN
    User.java
  repository/
    UserRepository.java
  service/
    AuthService.java        — register, login with JWT
    CustomUserDetails.java  — Wraps User for Spring Security
    UserDetailsServiceImpl.java — Loads UserDetails by username

TicketingPlatformApplication.java
```

### Test Source (`src/test/java/com/ticketing/`)

```
booking/service/
  TicketTierServiceTest.java        — 3 tests: getAvailableCount, tier not found [PASSING]

common/config/
  TestSecurityConfig.java           — @TestConfiguration, @EnableMethodSecurity,
                                      in-memory UserDetailsService (ADMIN + ORGANIZER users),
                                      HttpStatusEntryPoint(UNAUTHORIZED) for 401 enforcement,
                                      NO JwtFilter in test chain
common/security/
  JwtFilterTest.java                — [PASSING]

event/controller/
  CategoryControllerTest.java       — 5 tests: ADMIN create/delete 200, ORGANIZER 403, public GET 200 [PASSING]
  EventControllerTest.java          — 6 tests [PASSING]
  EventSearchControllerTest.java    — 3 tests: valid search 200, no params 200, oversized query 400 [PASSING]
  VenueControllerTest.java          — 7 tests: ADMIN create/update/delete 200, ORGANIZER 403, unauthenticated 401, public GET 200 [PASSING]

event/service/
  CategoryServiceTest.java          — 7 tests [PASSING]
  EventSearchServiceTest.java       — 2 tests [PASSING]
  EventServiceTest.java             — 7 tests [PASSING]
  VenueServiceTest.java             — 7 tests [PASSING]

ticketing_platform/
  TestcontainersConfiguration.java  — PostgreSQL + Redis containers
  TestTicketingPlatformApplication.java
  TicketingPlatformApplicationTests.java — 2 ERRORS (requires Docker Desktop — pre-existing blocker)

user/controller/
  AuthControllerTest.java           — 3 tests [PASSING]
user/service/
  AuthServiceTest.java              — 5 tests [PASSING]
```

### Database Migrations (`src/main/resources/db/migration/`)

| File | Contents | Status |
|------|----------|--------|
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

**NEXT MIGRATION MUST BE: V11__...**

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
|--------|----------|-------------|--------|---------------|
| Fix 1.1 | CRITICAL | Instant vs LocalDateTime on all entities | ✅ Done | All entities |
| Fix 1.2 | IMPORTANT | ENUM type for user_role in SQL | ✅ Done | V1 migration |
| Fix 1.3 | GOOD | deleted_at TIMESTAMPTZ on bookings | ✅ Done | V5 migration |
| Fix 2.1 | IMPORTANT | @Transactional(readOnly=true) at class level | ✅ Done | EventService, AuthService, UserDetailsServiceImpl, VenueService, CategoryService, EventSearchService |
| Fix 2.2 | IMPORTANT | @RequiredArgsConstructor, zero @Autowired | ✅ Done | All service + controller classes |
| Fix CC-1 | GOOD | X-Correlation-ID in all log statements | ⚠️ PARTIAL | Applied Day 1/2 files. **NOT YET applied to VenueService, CategoryService, EventSearchService** — defer to Day 7 |
| Fix CC-2 | IMPORTANT | No magic numbers, BusinessConstants only | ✅ Done | BusinessConstants.java, all service files |
| Fix 5.1 | CRITICAL | Lua floor guard in InventoryService | ⬜ Day 5 | |
| Fix 5.2 | IMPORTANT | InventoryWarmupHealthIndicator | ⬜ Day 5 | |
| Fix 7.1 | IMPORTANT | @Version on Booking and TicketTier | ⬜ Day 7 | |
| Fix 8.1 | CRITICAL | TOCTOU double-check inside Redis lock | ⬜ Day 8 | |
| Fix 8.2 | IMPORTANT | CheckInGuard two-layer protection | ⬜ Day 8 | |
| Fix 8.3 | IMPORTANT | ExpiryJob distributed lock | ⬜ Day 8 | |
| Fix 8.4 | IMPORTANT | Booking.expiresAt uses BusinessConstants | ⬜ Day 8 | |
| Fix 9.1 | CRITICAL | StripeWebhookController NOT @Transactional | ⬜ Day 9 | |
| Fix 9.2 | CRITICAL | DataIntegrityViolationException idempotency | ⬜ Day 9 | |
| Fix 10.1 | IMPORTANT | DLQ listeners declared | ⬜ Day 10 | |
| Fix 10.2 | IMPORTANT | Async QR generation via queue | ⬜ Day 10 | |
| Fix 11.1 | IMPORTANT | CANCELLED state in state machine | ⬜ Day 8 | |
| Fix 11.2 | IMPORTANT | RELEASE event / AVAILABLE state clarified | ⬜ Day 11 | |
| Fix 12.1 | GOOD | Refund days calculated with ChronoUnit | ⬜ Day 12 | |
| Fix 16.1 | CRITICAL | 80% test coverage gate before deploy | ⬜ Day 16 | |

---

## 5. DAY-BY-DAY COMPLETION STATE

| Day | Theme | Status | Tests |
|-----|-------|--------|-------|
| 0 | Pre-flight | ✅ | — |
| 1 | Project Init + Entities + Migrations | ✅ | Passing |
| 2 | Event Domain + Auth (JWT) | ✅ | 20/20 |
| 3 | Venue + Category + Search + Security Hardening | ✅ | 56/56 |
| 4 | Next.js Frontend + Home Page | ✅ | Passing |
| 5–21 | See PROGRESS.md | ⬜ | — |

---

## 6. GLOBALEXCEPTIONHANDLER — CURRENT HANDLERS

All exceptions must flow through `GlobalExceptionHandler`. Current mapping:

| Exception | HTTP Status | Handler Method |
|-----------|-------------|----------------|
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
|--------|----------|---------------|------|
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
|---------|--------|------------|
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

## 10. NEXT SESSION START — DAY 5

**Branch to create:** `git checkout -b day-05-inventory-rabbitmq`

**First task:** Redis + Lua Inventory & RabbitMQ setup
- Add Redis for concurrent ticket inventory management.
- Implement `InventoryService.java` with a Lua script to reserve tickets safely and prevent overselling (Fix 5.1).
- Add `InventoryWarmupHealthIndicator` (Fix 5.2).
- Configure RabbitMQ exchanges, queues, and bindings for async events.

**Important Note:** Make sure Docker Desktop is running when working on Day 5, as Redis and RabbitMQ will require Testcontainers for validation.
