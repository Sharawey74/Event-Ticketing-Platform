# PROGRESS.md — Event Ticketing Platform

## Implementation Status Tracker

> **Started:** Day 0 (not yet begun) | **Target End:** Day 21

---

## Day-by-Day Status

| Day | Theme | Status | Test Count | Tests | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 0 | Pre-flight Setup | ✅ Complete | — | — | Run constitution, verify scripts |
| 1 | Project Init + Entities + Migrations | ✅ Complete | — | Passing | |
| 2 | Event Domain + Auth (JWT) | ✅ Complete | 20 | 20/20 passing ✅ | Event/Auth services, endpoints, and Application Context validated |
| 3 | Venue + Category + Search | ✅ Complete | 36 | 56/56 passing ✅ | 2 Docker tests skipped. Venue/Category/Search services complete. |
| 4 | Next.js Frontend + Home Page | ✅ Complete | — | Passing | Scaffolded Next.js, added standard search + details, lint and build green |
| 5 | Inventory (Redis + Lua) + RabbitMQ Config | ✅ Complete | 7 | 63/63 passing | Implemented Lua floor guard, Warmup Health Indicator, Redis caching, RabbitMQ DLQs |
| 6 | N+1 Fixes + Integration Tests | ✅ Complete | 5 | 68/68 passing | @EntityGraph on EventRepo/BookingRepo, EventIntegrationTest, BookingIntegrationTest, k6 baseline |
| 7 | Week 1 Cleanup + Docker Compose | ✅ Complete | 0 | 68/68 passing | Docker Compose healthchecks, pgAdmin, Redis UI, Event Detail API |
| 8 | Booking State Machine | ✅ Complete | 5 | 73/73 passing | Implemented SSM 4.0.0, BookingService TOCTOU guard, CheckIn dual guard |
| 9 | Stripe Checkout + Webhook | ✅ Complete | 7 | 80/80 passing | Checkout session creation, webhook processing, idempotency |
| 10 | RabbitMQ Consumers + Notifications | ✅ Complete | 3 | 83/83 passing | Implemented BookingNotificationListener for async emails and QR code generation |
| 11 | Pricing Engine + Waitlist | ✅ Complete | 5 | 88/88 passing | Pricing engine logic, waitlist implementation, cancel booking action |
| 12 | Refund Logic + Concurrency Polish | ✅ Complete | 11 | 99/99 passing | 3-tier refund logic, Pricing Engine wired |
| 13 | Frontend Event Detail + Booking Flow | ✅ Complete | — | — | TicketTierSelector, Book Now routing, and confirmation UI built |
| 14 | Frontend User Dashboard + QR Display | ✅ Complete | — | — | Dashboard bookings table, booking details with base64 QR display |
| 15 | Frontend Organizer Dashboard + Core Fixes | ✅ Complete | — | — | Organizer dashboard scaffolded. Core bugs fixed: Stripe webhook 403, Session GSON fallback, QR pub/sub event missing, DTO nesting mismatches |
| 16A | Platform Stabilization (Day 16A) | ✅ Complete | 116 | 104/104 unit passing ✅ | BookingQueryService (QR gate), AuthService organizer role, register flow, DataSeeder profile fix, env alignment. 12 Docker-only errors (pre-existing). UI sub-session: edit event page created, loading-state bug fixed, hero Sign In hidden when authenticated, GlobalExceptionHandler 405/404/415 handlers added |
| 16B-CF | Checkout Flow Stabilization (sub-session) | ✅ Complete | 141 | 129/129 unit passing ✅ | 6 root-cause bugs fixed: price sync, resume checkout (Payment upsert fixes UNIQUE violation), PAYMENT_PENDING cancel+auto-expire, stale-closure dialog, explicit replace prompt, Booking History actions. +25 new tests. |
| 16B | Backend Test Coverage Push (80%+) | ✅ Complete | 183 | 183/183 passing ✅ | JaCoCo 81.4% INSTRUCTION coverage — gate passed. Fix 16.1 (Lua concurrency) done. BookingControllerTest (13 tests), +54 new unit tests across 13 new test classes. |
| 17 | Docker Multi-stage + Compose Polish | ✅ Complete | — | N/A (Docker verified, not unit-tested) | Multi-stage Dockerfile (JDK builder → JRE runtime, non-root `appuser`, 619MB), `.dockerignore`, `app` service wired into `docker-compose.yml` with `service_healthy` deps (Fix 7.2), E-009 (X-Frame-Options + HSTS on backend), E-009F (CSP + X-Frame-Options + X-Content-Type-Options on frontend, no Railway wildcard). Full stack verified via `docker-compose up -d`: all 7 containers healthy, `/actuator/health` UP, seed data present, headers confirmed on both backend and frontend. |
| 18 | CI/CD Pipeline (GitHub Actions) | ⬜ Not Started | — | — | |
| 19 | Performance + k6 Load Tests + Swagger/OpenAPI | ⬜ Not Started | — | — | E-002 Swagger annotations, k6 Railway baseline + booking scenarios |
| 20 | Code Quality + Security Hardening (M-002, M-004) | ⬜ Not Started | — | — | Bucket4j rate limiting, JWT denylist, CC-1/CC-2 final audit |
| 21 | Final Cleanup + Deploy to Railway + Vercel | ⬜ Not Started | — | — | Production smoke test, env var audit, README, KNOWN_ISSUES |

---

## Overlay Fixes Status

| Fix ID | Severity | Day | Applied | Notes |
| :--- | :--- | :--- | :--- | :--- |
| Fix 1.1 | CRITICAL | 1 | ✅ | Instant vs LocalDateTime on all entities |
| Fix 1.2 | IMPORTANT | 1 | ✅ | ENUM type for user_role in SQL |
| Fix 1.3 | GOOD | 1 | ✅ | deleted_at TIMESTAMPTZ on bookings table |
| Fix 2.1 | IMPORTANT | 2 | ✅ | Applied on EventService, AuthService, UserDetailsServiceImpl |
| Fix 2.2 | IMPORTANT | 2 | ✅ | Applied in all new services/controllers/security classes |
| Fix 5.1 | CRITICAL | 5 | ✅ | Lua floor guard in InventoryService |
| Fix 5.2 | IMPORTANT | 5 | ✅ | InventoryWarmupHealthIndicator |
| Fix 7.1 | CRITICAL | 7 | ✅ | @Version on Booking and TicketTier |
| Fix 7.2 | IMPORTANT | 7 | ✅ | service_healthy in Docker Compose |
| Fix CC-1 | GOOD | All | ✅ | X-Correlation-ID in all log statements |
| Fix CC-2 | IMPORTANT | All | ✅ | No magic numbers — BusinessConstants only |
| Fix A.1 | CRITICAL | Audit | ✅ | BUG-01: InventoryService warmup now loads DB tiers into Redis |
| Fix A.2 | IMPORTANT | Audit | ✅ | BUG-04: TicketTier magic number replaced with BusinessConstants.MAX_TICKETS_PER_BOOKING |
| Fix A.3 | CRITICAL | Audit | ✅ | BUG-02: BookingState: CANCELLED added, AVAILABLE+RELEASED removed |
| Fix A.4 | CRITICAL | Audit | ✅ | BUG-03: BookingRepository: findByStateAndExpiresAtBefore + findByIdWithLock added |
| Fix 8.1 | CRITICAL | 8 | ✅ | TOCTOU double-check inside lock |
| Fix 8.2 | IMPORTANT | 8 | ✅ | CheckInGuard two-layer protection |
| Fix 8.3 | IMPORTANT | 8 | ✅ | ExpiryJob distributed lock |
| Fix 9.1 | CRITICAL | 9 | ✅ | StripeWebhookController NOT @Transactional |
| Fix 9.2 | CRITICAL | 9 | ✅ | DataIntegrityViolationException idempotency |
| Fix 10.1 | IMPORTANT | 10 | ✅ | DENY_REFUND notification action |
| Fix 10.2 | IMPORTANT | 10 | ✅ | Async QR generation queue configured (Day 5); consumer implementation Day 10 |
| Fix 11.1 | IMPORTANT | Audit | ✅ | CANCELLED state added to BookingState (pre-applied for Day 8) |
| Fix 11.2 | IMPORTANT | 11 | ✅ | RELEASE event / AVAILABLE state removal (documented in BookingState Javadoc) |
| Fix 12.1 | GOOD | 12 | ✅ | refund_denial_reason via V11 migration |
| Fix 15.1 | CRITICAL | 15 | ✅ | Stripe Webhook 403 Forbidden fixed in SecurityConfig |
| Fix 15.2 | CRITICAL | 15 | ✅ | Stripe Session Deserialization Version Mismatch fixed via ApiResource.GSON fallback |
| Fix 15.3 | CRITICAL | 15 | ✅ | Missing BookingConfirmedEvent publish call added in WebhookService |
| Fix 15.4 | HIGH | 15 | ✅ | Next.js middleware token missing issue fixed (localStorage vs cookie sync) |
| Fix 15.5 | HIGH | 15 | ✅ | Dashboard nested BookingDetailsResponse DTO alignment (totalPrice, event.title) |
| Fix 16A.1 | CRITICAL | 16A | ✅ | BookingQueryService: QR/ticket data gated behind CONFIRMED/ATTENDED state |
| Fix 16A.2 | CRITICAL | 16A | ✅ | AuthService: ADMIN self-registration blocked; ORGANIZER role correctly assignable |
| Fix 16A.3 | HIGH | 16A | ✅ | register/page.tsx: sends role to backend; redirects to /auth/login?registered=true |
| Fix 16A.4 | HIGH | 16A | ✅ | DataSeeder: removed "default" profile (prod safety) |
| Fix 16A.5 | MEDIUM | 16A | ✅ | application-local.yml: fixed STRIPE_WEBHOOK_SECRET env var name |
| Fix 16A.6 | MEDIUM | 16A | ✅ | .env.example: corrected backend port to 8088 |
| Fix 16A.7 | HIGH | 16A | ✅ | BookingRepository: added findByIdWithDetails with full EntityGraph |
| Fix 16A.8 | HIGH | 16A | ✅ | BookingController: removed direct repo injection; now delegates to BookingQueryService |
| Fix 16A.9 | MEDIUM | 16A | ✅ | Pre-existing test URL mismatches fixed (AuthControllerTest, StripeWebhookControllerTest) |
| Fix 16A.10 | MEDIUM | 16A | ✅ | WebhookServiceTest: added missing BookingEventPublisher mock + populated booking test data |
| Fix 16A.11 | HIGH | 16A | ✅ | GlobalExceptionHandler: added 405/404/415 handlers (was falling into 500 catch-all) |
| Fix 16A.12 | HIGH | 16A | ✅ | page.tsx hero: "Sign In" button hidden when user is already authenticated |
| Fix 16A.13 | HIGH | 16A | ✅ | organizer/events/page.tsx: split loading guard — disabled TanStack Query kept isLoading:true forever when !token |
| Fix 16A.14 | HIGH | 16A | ✅ | organizer/events/[id]/edit/page.tsx: created missing edit event page (was 404) |
| Fix 16B.2 | CRITICAL | 16B-CF | ✅ | PaymentService: resume from PAYMENT_PENDING; Payment upsert (fixes payments_booking_id_key UNIQUE violation) |
| Fix 16B.3 | HIGH | 16B-CF | ✅ | GlobalExceptionHandler: IllegalStateException → 409 Conflict |
| Fix 16B.4 | HIGH | 16B-CF | ✅ | BookingService: self-cancel of PAYMENT_PENDING (releases seats, transitions to CANCELLED) |
| Fix 16B.5 | HIGH | 16B-CF | ✅ | ReservationExpirationJob: auto-expire stale PAYMENT_PENDING → PAYMENT_FAILED, release inventory |
| Fix 16B.6 | IMPORTANT | 16B-CF | ✅ | BusinessConstants: STRIPE_SESSION_TTL_SECONDS = 1860L |
| Fix 16B.7 | HIGH | 16B-CF | ✅ | TicketTierSelector/CartDrawer: price row + Total from store.totalAmount; savings line on discount |
| Fix 16B.8 | HIGH | 16B-CF | ✅ | ReservationGuard: live getState() reads eliminate stale-closure "Leave site?" dialog |
| Fix 16B.9 | HIGH | 16B-CF | ✅ | TicketTierSelector: explicit replace-confirmation before overwriting a reservation for a different event |
| Fix 16B.10 | HIGH | 16B-CF | ✅ | Booking History: Resume+Cancel actions; useEffect countdown fix; muted terminal rows; clear store on mount |
| Fix 16B.11 | MEDIUM | 16B-CF | ✅ | reservationStore: unitPrice + eventId fields added |
| Fix 16.1 | CRITICAL | 16B | ✅ | Concurrency test for reserveSeat() Lua script — 100-thread / 50-seat, startLatch, exactly 50 succeed |
| Fix PW3-1 | CRITICAL | 1/2 | ✅ | Stripe account + CLI installed |
| Fix E-009 | SECURITY | 17 | ✅ | X-Frame-Options: DENY + HSTS (1yr, includeSubDomains) added to SecurityConfig.java — no CSP (backend is JSON+Swagger only) |
| Fix E-009F | SECURITY | 17 | ✅ | CSP + X-Frame-Options + X-Content-Type-Options added to next.config.ts — connect-src derived from NEXT_PUBLIC_API_URL, no *.railway.app wildcard (deferred to Day 21) |

---

## Key Metrics (fill in each day)

| Metric | Current | Target |
| :--- | :--- | :--- |
| `./mvnw test` passing | 183 / 183 passing (all tests including Docker-based integration tests) | 100% |
| Test coverage | 81.4% INSTRUCTION (6731/8268) — JaCoCo gate ✅ PASSED | 80%+ |
| Active @Autowired usages | 0 | 0 |
| Active LocalDateTime usages | 0 | 0 |
| Magic numbers in code | 0 | 0 |
| `existsByStripeEventId()` usages | 0 | 0 (Use DB Unique Constraint) |

---

## Update Instructions

After each session, update this file:

- Change ⬜ to ✅ for completed days and applied fixes
- Fill in the Metrics table
- Note any blockers in the Notes column
