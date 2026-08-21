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
| 18 | CI/CD Pipeline (GitHub Actions) + Production Deploy | ✅ Complete | — | 183/183 unchanged | `application-prod.yml` + CORS guard + CI-only GitHub Actions workflow (Railway/Vercel auto-deploy natively, no CLI token). Backend deployed live on Railway: RabbitMQ moved to CloudAMQP (Railway trial resource cap), `SPRING_PROFILES_ACTIVE=prod` fixed (was the missing var keeping app on `local` datasource), Flyway migrated all 12 versions against real Postgres, app fully started. Frontend live on Vercel. Final fix (`management.health.rabbit.enabled: false`) applied, pending push. Full narrative: `.claude/day-18-walkthrough.md`. |
| 19 | Performance + k6 Load Tests + Swagger/OpenAPI | ✅ Complete | 183 | 183/183 unchanged | Preceded by an unplanned production outage fix: Rabbit/Mail health indicators disabled, explicit RabbitConnectionConfig (fixes localhost fallback + auto-recovery hang), server.port hardcoded to 8088 (fixes Railway proxy/app port mismatch) — backend confirmed live and healthy on Railway. Then: springdoc-openapi wired in, all 9 controllers annotated, load-test.js bugs fixed, booking-reservation.js + inventory-pressure.js added, PERFORMANCE.md created. k6 scripts not yet run against live Railway — results pending next session. |
| 20 | Code Quality + Security Hardening (M-002, M-004) | ✅ Complete | 191 | 191/191 passing ✅ | SECURITY-6 (`/actuator/**` ADMIN-only, health stays public), M-002 (Redis Lua rate limiting on auth/booking, gated by `app.rate-limit.enabled`), M-004 (JWT `jti` + Redis denylist + `/logout`), D19-1 (`TicketTier.availableCount` DB counter now symmetric across reserve/cancel/expire — a decrement-only fix would have leaked inventory, so the abandoned-hold expiry path was fixed too), CC-1 audit (3 `log.error` calls fixed to stop discarding stack traces). 82% INSTRUCTION coverage (verified via `./mvnw clean verify`), JaCoCo gate ✅ PASSED. Full narrative: `.claude/day-20-walkthrough.md`. |
| 21 | Final Cleanup + Deploy to Railway + Vercel | 🟡 In Progress | 194 | 194/194 passing ✅ | Vercel 404 fixed, production registration bug fixed (CORS + `NEXT_PUBLIC_API_URL` protocol), Concurrency & Scalability Hardening complete (Fix 21-1), `README.md`/`KNOWN_ISSUES.md` written. Verified live and confirmed passing: Railway `/actuator/health` (200 UP), security headers (`X-Frame-Options: DENY`, HSTS) on `/api/events`, CORS preflight correctly reflects the Vercel origin, Swagger UI reachable, `/actuator` root correctly 403s non-admins, Vercel homepage 200 with CSP `connect-src` containing the real Railway URL, no hardcoded `sk_live_`/`pk_live_` anywhere in source. Verified directly against local Postgres: Fix 1.1 (all `bookings` timestamp columns are `timestamptz`), Fix 1.2 (`user_role` is a real Postgres ENUM), Fix 1.3 (`deleted_at` column present), Fix 7.1 (`version` column present). Still needs the user's action: the 6 browser-based Critical Path smoke tests (booking flow through real Stripe checkout, DevTools session-storage/cookie inspection, organizer check-in flow), Railway/Vercel dashboard env var audits (no CLI access available locally to verify these directly), and Stripe test-dashboard webhook delivery check. `capacity-ramp.js` run against live Railway (browse/search journeys only, by user's choice) — results pending completion, to be added to `PERFORMANCE.md`. Carries over: frontend must send `Idempotency-Key` header on booking creation before `app.rate-limit.enabled=true` reaches production. |
| 22 | Seed Data — 15 Egyptian Private Events (V13 migration) | ✅ Complete | 194 | 194/194 passing ✅ | Executed `docs/Core/22_seed_data_plan_egypt_private_events.md` on branch `feat/seed-data-frontend-refresh` (renamed from `day-22-seed-egypt-events`). `V13__seed_egypt_private_events.sql`: +1 category (Conference), +2 ORGANIZER users, +15 PUBLISHED events across 8 existing private venues, +30 ticket tiers. No museums/government-cultural sites used (by design). Verified against real local Postgres: Flyway history, exact row-count deltas, `/api/search/events` (unfiltered + Conference-filtered), `/api/events/{id}` detail resolution, organizer login (bcrypt hash round-trip confirmed) + `/organizer/events` dashboard via both API and real browser, public `/search` page render. `./mvnw clean verify`: 194/194 unchanged, JaCoCo gate ✅ (~83.8% INSTRUCTION, gate-scoped) — data-only migration, no Java changes, no hardcoded-count tests broke. |
| 22b | Frontend Design Handoff (Claude Design mockups) | ✅ Complete | N/A | N/A (frontend, no backend tests affected) | Applied 3 Claude Design exports (Landing, Dashboard, Organizer Dashboard) to the live Next.js frontend on the same branch. `page.tsx`: hero restyled (kept real search form + photo background per explicit instruction), added Feature Strip + Featured Events (real data, first 6 published events) + For-Organizers sections, removed category-chip filter (superseded by `/search`'s own filters), added `Conference` to `fallbackCategories`. `footer.tsx`: added Product + Support columns (no Company column), all links point to real routes (no invented `/pricing` or `/help-center`). `dashboard/bookings/page.tsx` + `organizer/events/page.tsx`: visual polish only, enriched empty states, all data-fetching logic untouched. Verified: `npm run lint` surfaced 9 pre-existing errors/7 warnings in 6 files this session never touched (confirmed via `git diff` — not introduced by this work); `npm run build` (the actual Vercel deploy step) succeeds cleanly and is unaffected by those lint errors; `npm run test` (vitest) 4/4 passing unchanged; full manual browser verification of all 3 pages plus footer against real seeded data (Day 22's 15 events, Conference category) with dev caches cleared and both servers restarted fresh. |
| 23 | Portfolio Site Bug Fixes (Bug 1: reservation 500, Bug 2: webhook rollback) | ✅ Complete | 196 | 196/196 passing ✅ | Two production bugs found while capturing GitHub Pages portfolio site screenshots, both fixed TDD (Red confirmed against pre-fix code, then Green). **Bug 1**: `BookingService.reserveTickets()` used a plain `findById()` leaving `venue`/`category` lazy; production's `open-in-view: false` closed the session before the controller read them, throwing `LazyInitializationException` → 500 despite the booking already committing. Fixed via the existing `findByIdWithDetails()`; new `BookingReservationLazyLoadingIntegrationTest` forces `open-in-view=false` and proves it end-to-end via MockMvc. **Bug 2**: `WebhookService.handlePaymentSuccess()` published to RabbitMQ unguarded inside the same transaction as the `CONFIRMED` state write and the idempotency insert; a broker hiccup rolled back everything, so a Stripe retry hit the same failure again. Fixed with try/catch around the publish call; new `WebhookServiceTest` case proves the booking still reaches `CONFIRMED` when the publisher throws. **Frontend**: confirmation page now gates its UI on real `booking.state` instead of always showing success. **Docs**: fixed a stale `/api/payments/webhook` path (missing `/v1`) in `docs/Core/11_stripe_payments.md`. Manually verified the frontend fix against a local dev backend (real reservation, DB state flipped `PAYMENT_PENDING`→`CONFIRMED`, refresh button re-fetch confirmed). Portfolio site (`site/`): replaced the `08-booking-detail-qr-ticket` screenshot with a real `CONFIRMED`+QR capture (local, since production isn't redeployed yet), updated its caption, removed a now-stale "webhook didn't land" note, fixed the nav logo `.mark` element (was a blank gradient square, now uses `favicon.svg` so the "E" glyph shows). Commits use plain Conventional Commit messages, no AI attribution, per explicit instruction. Nothing pushed yet — deploy and an optional real-production recapture of `06-booking-confirmation` are left to the user. |

| 24 | UI Enhancement (Track A) + Brand Mark + Payment Reconciliation Fallback | ✅ Complete | 202 | 202/202 passing ✅ | **Payment-reliability bug found by the user in a normal booking**: a paid booking stayed `PAYMENT_PENDING`. Root cause was environmental, not code — the Stripe account has **zero webhook endpoints** and Stripe cannot reach `localhost:8088`, so `checkout.session.completed` had never been delivered. As `WebhookService.handlePaymentSuccess` is the only exit from `PAYMENT_PENDING`, paid bookings were stranded and `ReservationExpirationJob` then sweeps them to `PAYMENT_FAILED` — card charged, booking failed. Booking 562 was charged twice (EGP 1,500 × 2) via the *Resume* action and ended `CANCELLED`. Fixed with `PaymentReconciliationService` + `POST /api/v1/bookings/{id}/sync-payment`: the success redirect's `session_id` (previously received and ignored) is used to verify with Stripe and confirm through the **same** `WebhookService.confirmPaidBooking` the webhook uses, extracted so the paths cannot drift. Idempotent, ownership-enforced, short-circuits when already settled, and logs at WARN so a missing webhook stays visible. Proven end to end: a real `4242` purchase with **no webhook relayed** self-healed to CONFIRMED with its QR (booking 564). Also: Track A of `DESIGN_TASKS.md` (success token, raw hex 41 → 1, `chart-theme.ts`, real `SalesChart` replacing a chart with hardcoded coordinates, motion system with reduced-motion guard), circular "e" brand mark, `.no-scrollbar` defined (it was referenced but never existed), and portfolio-site recaptures incl. a real purchase→refund pair. |

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
| Fix E-006 | GOOD | 18 | ✅ | Complete application-prod.yml using real property names (jwt.secret, frontend.url, stripe.*, rabbitmq.uri), no fallback secrets |
| Fix E-003 | IMPORTANT | 18 | ✅ | CORS startup guard in WebConfig — logs resolved frontend.url, refuses to start on wildcard origin |
| Fix 18-mvnw | CRITICAL | 18 | ✅ | Dockerfile `RUN chmod +x mvnw` + git executable-bit fix — Railway build was failing Permission denied (exit 126) |
| Fix 18-profile | CRITICAL | 18 | ✅ | SPRING_PROFILES_ACTIVE=prod added to Railway variables — app was silently on local profile/datasource without it |
| Fix 18-health | HIGH | 18 | ✅ Confirmed live on Railway | management.health.rabbit.enabled: false — RabbitMQ (CloudAMQP) connectivity was failing /actuator/health despite the app itself being fully healthy |
| Fix 19-mail | HIGH | 19 | ✅ | management.health.mail.enabled: false — SMTP is not configured in production; the Mail health indicator was also failing /actuator/health independently of the Rabbit indicator |
| Fix 19-rabbitconn | CRITICAL | 19 | ✅ | RabbitConnectionConfig: explicit connection factory built from spring.rabbitmq.uri, fails fast on a blank URI — Spring Boot's auto-configuration was silently defaulting to localhost:5672 in production despite RABBITMQ_URL being confirmed correct |
| Fix 19-autorecovery | CRITICAL | 19 | ✅ | setAutomaticRecoveryEnabled(false) + setTopologyRecoveryEnabled(false) on the hand-built RabbitMQ connection factory — the raw client's own recovery was conflicting with Spring AMQP's, causing the app to go fully unresponsive ~15-20 minutes after a healthy start |
| Fix 19-port | CRITICAL | 19 | ✅ | server.port hardcoded to 8088 in application-prod.yml (was `${PORT:8088}`) — Railway's injected PORT (8080) was overriding it, so the app listened on 8080 while the proxy (routed to the Dockerfile's EXPOSE 8088) got connection refused on every request |
| Fix E-002 | GOOD | 19 | ✅ | springdoc-openapi 2.8.17 wired in via OpenApiConfig; all 9 REST controllers annotated with @Tag/@Operation/@ApiResponse; StripeWebhookController marked @Hidden; SecurityConfig permits /swagger-ui/**, /swagger-ui.html, /v3/api-docs/**, /v3/api-docs.yaml |
| Fix 19-k6 | GOOD | 19 | ✅ (scripts ready, Railway run pending) | load-test.js port/path/body.data.content bugs fixed; booking-reservation.js + inventory-pressure.js added; PERFORMANCE.md created with setup guide and placeholder result tables |
| Fix SECURITY-6 | P1 | 20 | ✅ | `/actuator/**` was fully `permitAll()`; now only `/actuator/health` is public, everything else requires `ADMIN` — SecurityConfig.java |
| Fix M-002 | MEDIUM | 20 | ✅ | New `RateLimitFilter` (atomic Redis Lua INCR+EXPIRE) limits `/api/v1/auth/**` by IP and `POST /api/v1/bookings` by user, requires `Idempotency-Key` header, excludes Stripe webhook. `@Bean` in SecurityConfig (never a scanned `@Component`), gated by `app.rate-limit.enabled` (off by default, on in prod) — RateLimitFilter.java, SecurityConfig.java |
| Fix M-004 | MEDIUM | 20 | ✅ | JWT `jti` claim + Redis denylist; `JwtFilter` checks it after signature validation; new `POST /api/v1/auth/logout` — JwtService.java, JwtFilter.java, AuthController.java |
| Fix D19-1 | MEDIUM | 20 | ✅ | `TicketTier.availableCount` now decrements on `reserveTickets()` and is restored symmetrically on cancel, payment-pending expiry, AND reserved-hold expiry (the last path previously restored nothing — decrement-only would have permanently leaked inventory) — BookingService.java, ReservationExpirationJob.java |
| Fix 20-cc1 | GOOD | 20 | ✅ | CC-1 audit found 3 `log.error()` calls passing `ex.getMessage()` instead of the exception object, silently discarding stack traces — fixed in PaymentService.java (×2) and WebhookService.java (×1) |
| Fix 21-1 | CRITICAL | 21 (pre-session) | ✅ | `ObjectOptimisticLockingFailureException` handler added to `GlobalExceptionHandler` (13th handler, 409 not 500). A new end-to-end `BookingServiceReservationConcurrencyTest` (100 threads/50 seats through the full `reserveTickets()` path, not just Redis) then found a REAL bug: two different users reserving from the same tier concurrently could race on `TicketTier`'s `@Version` during the DB-mirror write — the loser's rollback did not undo the earlier Redis decrement, permanently leaking that seat. Fixed by replacing the JPA read-modify-write with an atomic conditional `UPDATE` (`TicketTierRepository.decrementAvailableCount`), not by widening the distributed lock (tried first, reverted — it collapsed throughput to ~1 success per 100 concurrent requests) — BookingService.java, TicketTierRepository.java, GlobalExceptionHandler.java |
| Fix 22-seed | GOOD | 22 | ✅ | `V13__seed_egypt_private_events.sql` — 15 PUBLISHED Egyptian private events, 1 new `Conference` category, 2 new `ORGANIZER` seed users, 30 ticket tiers. Museums/government-cultural sites excluded by design (private-ticketing-platform fit). Verified end-to-end against real Postgres + real browser session (organizer login, `/organizer/events`, public `/search`) — V13__seed_egypt_private_events.sql |
| Fix 22-design | GOOD | 22b | ✅ | Landing/Dashboard/Organizer Dashboard redesigned per 3 Claude Design mockup exports; footer expanded to Product+Support columns; `fallbackCategories` gained `Conference`. All real behavior (search, react-query fetching, EventCard, cart, auth-aware navbar) preserved by explicit instruction — page.tsx, footer.tsx, dashboard/bookings/page.tsx, organizer/events/page.tsx |
| Fix 23-bug1 | CRITICAL | 23 | ✅ | `BookingService.reserveTickets()`: `findById()` → `findByIdWithDetails()` — production's `open-in-view: false` caused a `LazyInitializationException` → 500 on `venue`/`category` access despite the booking already committing — BookingService.java |
| Fix 23-bug2 | CRITICAL | 23 | ✅ | `WebhookService.handlePaymentSuccess()`: RabbitMQ publish wrapped in try/catch — an unguarded publish failure was rolling back the `CONFIRMED` state write and the idempotency insert in the same transaction, so a Stripe retry hit the identical failure again — WebhookService.java |
| Fix 23-frontend | HIGH | 23 | ✅ | Confirmation page (`bookings/[id]/confirmation/page.tsx`) now gates its success UI on real `booking.state` (added to `BookingDetails`) instead of always showing "Booking Confirmed!"; adds a "Payment Processing" state with manual refresh, and an explicit "Payment Not Completed" state for terminal failures |
| Fix 23-docs | GOOD | 23 | ✅ | Fixed stale `/api/payments/webhook` path (missing `/v1`) in `docs/Core/11_stripe_payments.md`, 3 occurrences |
| Fix 23-site | GOOD | 23 | ✅ | Portfolio site: replaced `08-booking-detail-qr-ticket.webp` with a real `CONFIRMED`+QR capture, updated its `walkthrough.html` caption, removed a stale mid-processing note, fixed the nav bar `.mark` logo (was blank, now uses `favicon.svg`) |

| Fix 24-webhook | CRITICAL | 24 | ✅ | Paid bookings stranded at `PAYMENT_PENDING` because no Stripe webhook endpoint exists and Stripe cannot reach localhost. New `PaymentReconciliationService` + `POST /api/v1/bookings/{id}/sync-payment` verify the session on the success redirect and confirm via the shared `WebhookService.confirmPaidBooking`. Webhook remains primary — PaymentReconciliationService.java, WebhookService.java, BookingController.java, confirmation/page.tsx |
| Fix 24-slice | MEDIUM | 24 | ✅ | Adding a constructor dependency to `BookingController` broke the `@WebMvcTest` slice (13 context-load errors). Added the matching `@MockitoBean` — BookingControllerTest.java |
| Fix 24-trackA | GOOD | 24 | ✅ | `--color-success` token pair added (its absence is why a success green was hardcoded in 3 files); raw hex in frontend 41 → 1; `chart-theme.ts`; `SalesChart.tsx` replacing a chart whose SVG coordinates were hardcoded; motion system + `prefers-reduced-motion` guard |
| Fix 24-scrollbar | MEDIUM | 24 | ✅ | `.no-scrollbar` was referenced by the featured-events rail but never defined anywhere, so the native bar rendered; also a stray vertical scrollbar (a non-`visible` axis forces the other to `auto`). New `.scroll-rail` pins `overflow-y: hidden` — globals.css, page.tsx |
| Fix 24-brand | GOOD | 24 | ✅ | Circular "e" mark cut to transparency by saturation keying; shipped under a new filename because Next's image optimizer caches per (url, width, quality, format) and the browser's WebP variant stayed stale — eventora-mark-v2.png, favicon.ico, site/favicon.png |
| Fix 24-clidoc | GOOD | 24 | ✅ | `instructions.md` claimed the Stripe CLI was installed (Fix PW3-1 ✅). It is not — corrected, since that entry made the missing webhook look impossible |

---

## Key Metrics (fill in each day)

| Metric | Current | Target |
| :--- | :--- | :--- |
| `./mvnw test` passing | 202 / 202 passing (all tests including Docker-based integration tests) | 100% |
| Test coverage | JaCoCo gate ✅ PASSED, verified via `./mvnw clean verify` | 80%+ |
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
