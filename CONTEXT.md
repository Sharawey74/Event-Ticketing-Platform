# CONTEXT SNAPSHOT — Event Ticketing Platform

## Last Updated: Day 26 (2026-09-06) — Booking idempotency closed, and the docs/Core study path rebuilt against the source.

**Day 26 — `Idempotency-Key` now means something.** It was a doorman who checked you had a ticket
without ever reading it: `RateLimitFilter` rejected a blank header and did nothing with the value,
and the client minted a fresh `crypto.randomUUID()` per attempt, so a retry carried a different key
regardless. Both halves are fixed.

**Backend.** `V14__add_booking_idempotency_key.sql` adds `bookings.idempotency_key` with
`uq_bookings_idempotency_key`. The constraint is the guard — same shape as Fix 9.2's
`processed_stripe_events` — because an application-level "have I seen this key?" test is a
check-then-act race that two concurrent duplicates both pass. `BookingService` gained a **5-arg
overload** (not a signature change: ~15 existing test call sites keep working) that stamps the key.

The catch could not live in `BookingService`: by the time `DataIntegrityViolationException` is
raised that transaction is rollback-only, so it cannot read the winning row, and `@Transactional` is
proxy-based so a self-call would not create a new boundary. Hence `BookingIdempotencyService` — a
deliberately non-transactional bean that does a fast-path lookup, delegates, and on collision
re-reads the row the winner committed. A key is scoped to its user; a mismatch is **409**, never
someone else's booking. A violation with no matching key is **rethrown**, so a genuine constraint
bug is never reported as a duplicate.

**Frontend.** `TicketTierSelector` holds the key in a ref keyed by `eventId:tierId:quantity`, reuses
it across retries of the same intent, and clears it on success. Changing tier or quantity mints a
new key on purpose — replaying the old one would return the *original* booking and silently discard
the change the user just made.

⚠️ **The test that nearly shipped measuring nothing.** The first integration test passed while
proving only the fast-path lookup: the replay never reached the database, so the constraint could
have been absent entirely and everything would still have been green. There is now a case that
bypasses the wrapper and calls `BookingService` twice with the same key, asserting Postgres itself
refuses the second insert. **That one is the guarantee; the lookup is only speed.**

**Verification.** 217/217 (from 202: +7 unit on the guard, +4 integration on real PostgreSQL, +2 on
key stamping, +2 on the controller header). JaCoCo gate ✅ 83.8% INSTRUCTION gate-scoped. Because the
integration tests boot the full context under `ddl-auto: validate`, Flyway genuinely applied V1→V14
and Hibernate verified the column exists — the migration is proven, not assumed. Frontend: vitest
4/4, `npm run build` clean, lint unchanged at 9 errors / 4 warnings (all pre-existing, none in the
touched file).

**Docs.** The whole `docs/Core` study path was rebuilt against the source. The first two passes had
only checked facts (ports, versions, counts); this one checked the *code*, and ~110 fabricated or
incorrect identifiers were corrected — wrong Redis keys, wrong RabbitMQ exchange and queue names, a
state-machine doc describing six transitions and four classes that do not exist, `ApiResponse` fields
that are never returned, "all DTOs are records" in a codebase containing zero records. Every
technology doc also gained a vendor-neutral **What it is** definition above its project role, plus an
annotation reference in `05` and a table-to-entity walkthrough in `07`. `docs/` is gitignored, so
none of that appears in this branch's diff.

⚠️ **Two code gaps the docs audit found and documented rather than fixed** — both still open:
`CheckInGuard` is a stub returning `true` (so check-in has one real layer, not the two Fix 8.2
claims), and the RabbitMQ retry/DLQ ladder is unconfigured, so a throwing listener requeues in a
tight loop and never dead-letters.

## Previously — Day 25 (2026-09-06) — UI Redesign v2 (Phases 1–8) merged, and a booking-idempotency gap found while auditing it.

**Shipped.** The full `design_tasks.md` redesign, 71 commits across eight phase branches, merged to `main` as PRs #37–#45. Phase 1 restructured colour into three layers — primitive → semantic → component — and mapped Tailwind's `--color-*` namespace onto the semantic layer with **`@theme inline`**, which is the load-bearing detail: `inline` compiles each utility to `var(--sem-…)` rather than a frozen value, so Phase 7's dark mode is one `[data-theme="dark"]` block reassigning that layer, with no utility, component token or markup touched. Phase 1 was proven byte-identical on the built output before anything was built on it.

**Phases.** 1 tokens · 2 shell · 3 landing hero · 3b `/welcome` · 4 booking path · 5 attendee account · 6 organizer · 7 dark mode · 8 responsive, states, legal.

**Bugs the redesign surfaced and fixed.**
- **Security:** both ticket views fell back to `https://api.qrserver.com/v1/create-qr-code/?data=<ticket.code>`, putting the venue-door entry credential in a query string to a third party. Removed from both files in one commit so the leak was never live in an intermediate state.
- **Fabricated data:** `buildSalesSeries` spread real totals across 30 synthetic daily buckets with an invented weighting curve and labelled it "Sales over the last 30 days", with per-day hover figures. Replaced by `buildEventRevenueSeries` — one bar per event from real `grossRevenue`. Chart total verified equal to `SELECT sum(total_amount)`.
- **QR race:** removing the third-party fallback exposed a real one. The confirmation page fetched before RabbitMQ had written the codes, and a cosmetic two-second timer hid that it never re-fetched. Now polls until they arrive.
- **Accessibility:** the Material Symbols icon font renders through *ligatures*, so each glyph name was a real text node landing in the accessible name of its container — the legal back links announced as "arrow_back Back to Home", the register role options as "confirmation_numberAttend Events". Removed across 41 usages in 11 files, which also drops a render-blocking font request.
- **Contrast:** three controls flipped their fill with the theme while their label did not. `.btn-gradient` put white on pale violet at **1.71:1** in dark (sign-in and create-account); `.btn-glass` and the confirmation panel's "Total Paid" label had the same shape of problem, the latter at **1.36:1**. ⚠️ The first dark-mode sweep missed all three because it read `background-color`, and a gradient is a `background-image` — worth knowing for any future audit.
- `--violet-400` was referenced by `.aurora-pointer` and never defined. An undefined var inside `color-mix()` invalidates the whole declaration, so the hero pointer glow had **never once painted**.

**Verification.** 41 semantic token pairs measured on built output in both themes (dark matches light, lowest dark pair 6.13:1); six public routes swept live in both themes with zero failures; 375 / 768 / 1024 / 1440 signed in as an organizer — no horizontal page scroll, one `<main>`, no heading-order skips, no unnamed controls, no placeholder-only inputs. Frontend build passes, vitest 4/4, lint improved from 9 errors / 6 warnings to **9 / 4**. No backend code was touched, so the Java suite is unchanged at 202/202.

**⚠️ The open item — booking idempotency is nominal, not real.** The frontend now sends `Idempotency-Key` on `POST /api/v1/bookings`, which unblocks `app.rate-limit.enabled=true` in production and closes the Day 20 carryover *as written*. But it provides no actual idempotency: `RateLimitFilter` only checks the header is **present** (`RateLimitFilter.java:83`), never storing or comparing it, and the frontend mints a fresh `crypto.randomUUID()` on every attempt, so a retry would carry a different key regardless. The in-browser double-click is already blocked by `disabled={isSubmitting}`, so the exposure is narrow — a client-side timeout on a request that actually succeeded, two tabs, or an automatic network retry. That is the same failure mode that charged booking 562 twice. **Day 26 exists to close it**, using the `processed_stripe_events` pattern already proven in this codebase: UNIQUE constraint + catch `DataIntegrityViolationException`.

**Known deviations, deliberate.** Footer links are 24×24 rather than the plan's 44×44 — nine stacked links at 44 adds ~250px of column and the hit areas would overlap and steal each other's taps; 24px on a 12px gap clears WCAG 2.5.8 with room. Hover/active/disabled states were not exhaustively re-verified on every control; only controls with no focus indicator at all were fixed.

## Previously — Day 24 (2026-08-21) — UI enhancement (Track A), brand mark swap, portfolio-site recapture, and a **payment-reliability fix found by the user during a normal booking**.

**The headline bug.** A booking paid for through Stripe stayed `PAYMENT_PENDING` on the dashboard. Root cause was not application code: the Stripe account has **zero webhook endpoints registered**, and Stripe cannot reach `localhost:8088` regardless — so `checkout.session.completed` had never once been delivered. Since `WebhookService.handlePaymentSuccess` is the *only* path out of `PAYMENT_PENDING`, every paid booking was stranded, and `ReservationExpirationJob` then sweeps stale `PAYMENT_PENDING` to `PAYMENT_FAILED` and releases the seats — i.e. **the card is charged and the booking still fails**. Booking 562 was charged **twice** (EGP 1,500 × 2) because the stuck state leaves a *Resume* action in the UI that opens a fresh checkout session for the same booking; it ended `CANCELLED` with two live payments.

**The fix — `PaymentReconciliationService` + `POST /api/v1/bookings/{id}/sync-payment`.** Stripe returns `session_id` on the success redirect precisely so the app can verify synchronously; the confirmation page received it and ignored it. The new service retrieves the session, and when Stripe reports `payment_status=paid`, confirms through `WebhookService.confirmPaidBooking` — the *same* method the webhook now calls, extracted so the two paths cannot drift. Ownership is enforced object-level, non-`PAYMENT_PENDING` bookings short-circuit without touching Stripe (so the common path costs nothing), and a Stripe outage is swallowed rather than failing the page. It logs at **WARN** when it has to step in, so a missing webhook stays visible in ops instead of being silently masked. The confirmation page now calls it whenever the booking reads `PAYMENT_PENDING`.

Verified end to end against real Stripe test mode: a genuine purchase (card `4242`) with **no webhook relayed at all** landed on the confirmation page as `CONFIRMED` with its QR ticket issued — booking 564, intent `pi_3U6sjI…`, and the WARN line present in the backend log. Bookings 561/563 were rescued the same way. **The webhook remains the primary path; this only closes the gap when it does not arrive.**

**Also this day.** Track A of `DESIGN_TASKS.md` delivered: added the missing `--color-success` token pair, took raw hex values in the frontend from **41 → 1**, extracted chart colours to `src/lib/chart-theme.ts`, and replaced the organizer "Sales Over Time" chart — which had **hardcoded SVG path coordinates and drew the same curve regardless of data** — with `SalesChart.tsx` derived from real events, plus an explicit empty state. Added a motion system (scroll reveal, count-up, animated ticket field on the hero) with a full `prefers-reduced-motion` guard. Swapped the brand to the circular "e" mark, shipped under a new filename because Next's image optimizer caches per `(url, width, quality, format)` and the WebP variant the browser renders had gone stale. Fixed `.no-scrollbar`, which was referenced in the markup but **never defined**, and a stray vertical scrollbar on the featured rail. Recaptured the portfolio site's landing, featured-events, organizer-dashboard and attendee screenshots, and added a real purchase→refund pair.

⚠️ `instructions.md` listed "Fix PW3-1: Stripe account + CLI installed" as ✅ — **the Stripe CLI is not installed**. Corrected, because that stale entry is what made the missing webhook look like it should already have worked.

## Previously — Day 23 (2026-07-16) — Fixed 2 production bugs surfaced while building the GitHub Pages portfolio site, and polished the site itself. **Bug 1**: `BookingService.reserveTickets()` loaded `Event` via a plain `findById()`, leaving `venue`/`category` lazy; production's `spring.jpa.open-in-view: false` closed the Hibernate session before `BookingController` read those fields, throwing `LazyInitializationException` → 500 despite the reservation having already committed. Fixed by switching to the existing `findByIdWithDetails()`. **Bug 2**: `WebhookService.handlePaymentSuccess()` published a `BookingConfirmedEvent` to RabbitMQ with no error handling, inside the same transaction as the `CONFIRMED` state write and the idempotency insert — a broker hiccup threw an unchecked exception and rolled back everything, including the idempotency guard, so a Stripe retry hit the identical failure again. Fixed by catching and logging instead of rethrowing. **Frontend**: the booking confirmation page never checked `booking.state` before rendering "Booking Confirmed!" — now gates on real state (confirmed / terminal-failure / still-processing-with-refresh-button). Both backend fixes reproduced Red via new/updated tests before the fix and confirmed Green after; 196/196 tests passing, JaCoCo gate passed. **Portfolio site** (`site/`): replaced the `08-booking-detail-qr-ticket` screenshot — previously an honest "mid-processing" capture from before the fix — with a real capture of a `CONFIRMED` booking with a genuine QR code (captured locally against the fixed code, since the fix isn't deployed to Railway/Vercel yet), updated the corresponding `walkthrough.html` caption, removed a now-stale note about the webhook not landing during the original capture window, and fixed the nav bar's `.mark` logo element (was a blank gradient square with no glyph — now uses `favicon.svg` as its background-image so it matches the actual Eventora "E" mark). Nothing has been pushed; deploy and a real production re-capture of the confirmation screenshot are still pending, at the user's discretion.

## Previously — Day 22b (2026-07-15) — Frontend Design Handoff: applied 3 Claude Design mockup exports (Landing, Dashboard, Organizer Dashboard) to the live Next.js frontend. Landing page hero kept its real search form and photo background (explicit instruction) but gained a stats row, a "Become an Organizer" CTA, and three new sections (Feature Strip, Featured Events using real data, For-Organizers teaser); the old category-chip filter + full grid was removed in favor of a small featured-events teaser linking to `/search`. Footer gained Product + Support columns (all real routes, no invented pages). Dashboard and Organizer Dashboard pages got empty-state and visual polish only — no data-fetching logic touched. `npm run build` (the actual Vercel deploy step) succeeds cleanly; `npm run test` 4/4 unchanged; 9 pre-existing lint errors in 6 untouched files confirmed NOT introduced by this work (verified via `git diff`) and confirmed not to block `next build`.

## Previously — Day 22 (2026-07-15) — Seed Data: 15 real-world-plausible, privately organized Egyptian events added via `V13__seed_egypt_private_events.sql` (Flyway migration, IMMUTABLE), so the live storefront/dashboard have real content instead of empty states. Adds 1 new category (`Conference`), 2 new `ORGANIZER` seed users, 15 `PUBLISHED` events across 8 existing private/commercial venues (Cairo, Alexandria, Hurghada, Sharm El Sheikh, Dahab, El Gouna), and 30 ticket tiers (2 per event). Deliberately excludes museums and government-run cultural/antiquities venues by design. 194/194 tests passing (unchanged), ~83.8% INSTRUCTION coverage (gate-scoped) verified via `./mvnw clean verify`, JaCoCo gate passed.

## Previously — Day 20 (2026-07-03) — Code Quality + Security Hardening: restricted `/actuator/**` to ADMIN-only (only `/actuator/health` stays public), added Redis-Lua-backed rate limiting on auth/booking endpoints (M-002), added a JWT `jti` + Redis denylist with a new `/logout` endpoint (M-004), fixed a bug where `TicketTier.availableCount` in the database never decremented on reservation and could have permanently leaked inventory if fixed naively (D19-1), and audited/fixed 3 `log.error()` calls that were silently discarding stack traces (CC-1). 191/191 tests passing, 82% INSTRUCTION coverage verified via `./mvnw clean verify`, JaCoCo gate passed.

## Branch: feat/booking-idempotency (local, cut from `main` at PR #45 — **not pushed**). All redesign branches are merged and can be deleted.

## Test Status: 217/217 ALL passing (+15 in Day 26). Coverage 83.8% INSTRUCTION gate-scoped, JaCoCo gate ✅. Frontend: vitest 4/4, `npm run build` clean, lint 9 errors / 4 warnings (unchanged). **NEXT MIGRATION MUST BE: V15__**

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
| V12__add_egyptian_venues.sql | Additional real Egyptian tourism-city venues (Giza, Alexandria, Luxor, Aswan, Hurghada, Sharm El Sheikh, Dahab) | IMMUTABLE |
| V13__seed_egypt_private_events.sql | 1 new category (Conference), 2 new ORGANIZER users, 15 PUBLISHED Egyptian private events + 30 ticket tiers | IMMUTABLE |
| V14__add_booking_idempotency_key.sql | `bookings.idempotency_key` + `uq_bookings_idempotency_key` (Fix 26-idem) | IMMUTABLE |

**NEXT MIGRATION MUST BE: V15__...**

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
| **18-health** | `management.health.rabbit.enabled: false` — RabbitMQ (CloudAMQP) connectivity issue was failing `/actuator/health` and blocking Railway deploys even though the app itself was fully healthy | Infra | ✅ Confirmed live on Railway | application-prod.yml |
| **19-mail** | `management.health.mail.enabled: false` — SMTP is not configured in production; the Mail health indicator was independently failing `/actuator/health` alongside the Rabbit indicator | Infra | ✅ Applied | application-prod.yml |
| **19-rabbitconn** | `RabbitConnectionConfig` — explicit connection factory built from `spring.rabbitmq.uri`, fails fast on a blank URI instead of silently defaulting to `localhost:5672` | Infra | ✅ Applied | RabbitConnectionConfig.java |
| **19-autorecovery** | `setAutomaticRecoveryEnabled(false)` + `setTopologyRecoveryEnabled(false)` on the hand-built connection factory — the raw RabbitMQ client's own recovery was conflicting with Spring AMQP's, causing the app to go fully unresponsive ~15-20 minutes after a healthy start | Infra | ✅ Applied | RabbitConnectionConfig.java |
| **19-port** | `server.port` hardcoded to `8088` (was `${PORT:8088}`) — Railway's injected `PORT` (8080) was overriding it, so the app listened on 8080 while the proxy (routed to the Dockerfile's `EXPOSE 8088`) got connection refused on every request | Infra | ✅ Applied | application-prod.yml |
| **E-002** | springdoc-openapi 2.8.17 wired in via `OpenApiConfig`; all 9 REST controllers annotated with `@Tag`/`@Operation`/`@ApiResponse`; `StripeWebhookController` marked `@Hidden`; `SecurityConfig` permits `/swagger-ui/**`, `/v3/api-docs/**` | Docs | ✅ Applied | OpenApiConfig.java, SecurityConfig.java, all 9 controllers |
| **19-k6** | `load-test.js` port/path/`body.data.content` bugs fixed; `booking-reservation.js` + `inventory-pressure.js` added; `PERFORMANCE.md` created with setup guide and placeholder result tables | Tests | ✅ Applied (scripts ready, Railway run pending) | src/test/k6/*.js, PERFORMANCE.md |
| **SECURITY-6** (Day 20) | `/actuator/**` was fully `permitAll()` — closed to ADMIN-only, `/actuator/health` stays public for Docker/Railway healthchecks | Security | ✅ Applied | SecurityConfig.java |
| **M-002** (Day 20) | Rate limiting via `RateLimitFilter` — atomic Redis Lua `INCR`+`EXPIRE` (same pattern as `DistributedLockService`), limits `/api/v1/auth/**` by IP and `POST /api/v1/bookings` by authenticated user, requires `Idempotency-Key` header, excludes Stripe webhook. `@Bean` in `SecurityConfig` (never a scanned `@Component`, so never loaded in `@WebMvcTest` slices), gated by `app.rate-limit.enabled` (off by default, on in prod — no dedicated `test` Spring profile exists in this project) | Security | ✅ Applied | RateLimitFilter.java, SecurityConfig.java, BusinessConstants.java, application-prod.yml |
| **M-004** (Day 20) | JWT `jti` claim added to every token; `revokeToken()`/`isTokenRevoked()` denylist logic lives in `JwtService` (already mocked in every `@WebMvcTest` slice, so no test files needed to change); `JwtFilter` checks the denylist after signature validation, before `SecurityContext` population; new `POST /api/v1/auth/logout` | Security | ✅ Applied | JwtService.java, JwtFilter.java, AuthController.java, BusinessConstants.java |
| **D19-1** (Day 20) | `BookingService.reserveTickets()` only ever decremented the Redis inventory counter, never the DB-persisted `TicketTier.availableCount` — `GET /api/events/{id}` showed a stale, falsely-high seat count during active holds (confirmed via k6 + direct Redis inspection in Day 19). Fixed with a DB-side decrement on reserve; a decrement-only fix would have permanently leaked inventory, since the abandoned-RESERVED-hold expiry path (`ReleaseSeatsAction` is a Redis-release stub only) restored nothing — so the release was added there too, mirroring the existing `cancelBooking`/`expireStalePaymentPending` increment pattern | Booking | ✅ Applied | BookingService.java, ReservationExpirationJob.java |
| **20-cc1** | CC-1 audit found 3 `log.error()` calls passing `ex.getMessage()` instead of the exception object, silently discarding the stack trace, on Stripe session creation failure, Stripe refund failure, and the Stripe GSON deserialization fallback | Payment | ✅ Applied | PaymentService.java, WebhookService.java |
| **21-1** (Day 21 pre-session) | `GlobalExceptionHandler` had no handler for `ObjectOptimisticLockingFailureException` (fell through to 500) despite `@Version` on 3 entities — added as its 13th handler (409). Writing the TDD-mandated extended concurrency test for this (`BookingServiceReservationConcurrencyTest`, 100 threads/50 seats through the FULL `reserveTickets()` path, not just Redis) then surfaced a real, previously-undocumented bug: two different users concurrently reserving from the same tier raced on `TicketTier`'s `@Version` during the DB-mirror write (D19-1's decrement); the loser's rollback did not undo the earlier Redis decrement, permanently leaking that seat (not an oversell — a silent, unrecoverable capacity loss). Widening the distributed lock from per-user-per-tier to per-tier-only was tried first and reverted — it closed the leak but collapsed throughput to ~1 success per 100 concurrent requests, since the lock is fail-fast with no retry/backoff. Fixed instead by replacing the JPA read-modify-write with a single atomic conditional SQL `UPDATE` (`TicketTierRepository.decrementAvailableCount`, `WHERE available_count >= :quantity`), which has no read-then-write gap regardless of lock granularity | Booking | ✅ Applied | GlobalExceptionHandler.java, BookingService.java, TicketTierRepository.java |
| **25-tokens** (Day 25) | Three-layer token system: primitive → semantic → component, with Tailwind's `--color-*` namespace mapped onto the semantic layer via `@theme inline`. `inline` is load-bearing — it compiles utilities to `var(--sem-…)` instead of a frozen value, which is what makes a runtime theme swap possible at all | Frontend | ✅ Applied | globals.css |
| **25-dark** | Dark mode as a single `[data-theme="dark"]` block reassigning L2. Pre-paint inline script in `<head>` stamps the attribute before first paint (anything deferred flashes the wrong theme); toggle persists to `localStorage` and defaults to `prefers-color-scheme`. The M3 `-fixed` roles are deliberately NOT redeclared — that is what keeps the ten booking status chips stable across themes | Frontend | ✅ Applied | globals.css, lib/theme.ts, ui/ThemeToggle.tsx, layout.tsx |
| **25-qrleak** | **SECURITY.** Both ticket views fell back to `api.qrserver.com/v1/create-qr-code/?data=<ticket.code>` — the venue-door entry credential in a query string to a third party. Removed from both files in one commit so the leak was never live in an intermediate state | Security | ✅ Applied | confirmation/page.tsx, dashboard/bookings/[id]/page.tsx |
| **25-qrrace** | Removing that fallback exposed a real race: the confirmation page fetched before RabbitMQ had written the codes, and a cosmetic `setTimeout(2000)` shimmer hid the fact that it never re-fetched. Replaced with real polling (600ms interval, 12s ceiling, cancellation flag) | Booking | ✅ Applied | bookings/[id]/confirmation/page.tsx |
| **25-chart** | `buildSalesSeries` spread real totals across 30 synthetic daily buckets with an invented weighting curve, labelled "Sales over the last 30 days" with per-day hover figures — fabricated data presented as real. Replaced by `buildEventRevenueSeries`: one bar per event from real `grossRevenue`, verified equal to `SELECT sum(total_amount)` | Frontend | ✅ Applied | lib/chart-theme.ts, organizer/SalesChart.tsx |
| **25-iconfont** | **A11Y.** Material Symbols renders through ligatures, so each glyph name was a real text node landing in its container's accessible name ("arrow_back Back to Home", "confirmation_numberAttend Events"). 41 usages across 11 files replaced with inline SVG; the render-blocking font request is gone. Removing it also un-named several icon-only controls, which were then given explicit `aria-label`s | A11y | ✅ Applied | 11 page/component files, layout.tsx |
| **25-contrast** | Three controls flipped their fill with the theme while their label did not: `.btn-gradient` at **1.71:1** in dark (sign-in / create-account), `.btn-glass`, and the confirmation panel's "Total Paid" label at **1.36:1**. ⚠️ The dark-mode sweep missed all three because it reads `background-color` and a gradient is a `background-image` — a known blind spot for any future audit | Frontend | ✅ Applied | auth/login, auth/register, confirmation/page.tsx, globals.css |
| **25-violet400** | `--violet-400` was referenced by `.aurora-pointer` and never defined. An undefined var inside `color-mix()` invalidates the entire declaration, so the hero pointer glow had never once painted | Frontend | ✅ Applied | globals.css |
| **25-welcome** | `/welcome` rebuilt full-bleed on a new scene (morphing light fields, canvas particle field). Pinned to the viewport — `overflow-x-hidden` alone had made it its own scroll container, since a box that is not `visible` on one axis computes to `auto` on the other. No cursor-driven motion. Ambient pulse peak lowered after measuring the composited ground swing to #b655cf, which took the tagline to 2.94:1 | Frontend | ✅ Applied | welcome/page.tsx, welcome/ParticleField.tsx, globals.css |
| **26-idem** (Day 26) | **CLOSED.** `bookings.idempotency_key` + `uq_bookings_idempotency_key` (V14) is the guard; `BookingIdempotencyService` sits outside the transaction (it must — the exception leaves the tx rollback-only, and `@Transactional` is proxy-based) and returns the booking the key already created. Keys are user-scoped: a mismatch is 409. A violation with no matching key is rethrown, so a real constraint bug is never masked as a duplicate. The client reuses one key per intent, keyed on `eventId:tierId:quantity`. Proven end to end against real PostgreSQL, including a case that bypasses the fast path so the constraint itself is what refuses the duplicate | Booking | ✅ Applied | V14 migration, Booking.java, BookingRepository.java, BookingService.java, BookingIdempotencyService.java, BookingController.java, TicketTierSelector.tsx |
| **26-checkin** | ⚠️ **OPEN — found during the Day 26 docs audit.** `CheckInGuard.evaluate()` is a stub that logs and returns `true`. No event-date check, no organizer-ownership check — so Fix 8.2's "CHECK_IN dual-guard" is one layer, not two: any ORGANIZER can check in any CONFIRMED booking on any date. The hook is wired correctly and *is* consulted; only the body is missing | Booking | ⬜ Open | CheckInGuard.java |
| **26-dlq** | ⚠️ **OPEN — found during the Day 26 docs audit.** The RabbitMQ dead-letter queues are declared, but no listener retry properties exist (`spring.rabbitmq.listener.simple.retry.*`, `default-requeue-rejected`). Under Spring Boot's defaults a throwing listener requeues in a tight loop and never reaches a DLQ, so the documented "3 retries then DLQ" behaviour does not happen | Infra | ⬜ Open | application-*.yml, RabbitMQConfig.java |

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
| 19 | Production Deploy Stabilization + Swagger/OpenAPI + k6 Load Tests | ✅ Complete | 183/183 unchanged — production outage fully root-caused and fixed (Rabbit/Mail health indicators, RabbitMQ localhost fallback + auto-recovery hang, server-port mismatch), backend confirmed live and healthy on Railway. springdoc-openapi wired in with all 9 controllers annotated; k6 scripts fixed/extended, results pending a live Railway run. See Section 10 for full narrative. |
| 20 | Code Quality + Security Hardening (M-002, M-004) | ✅ Complete | 191/191 passing (+8 new) — 82% INSTRUCTION coverage, JaCoCo gate ✅. SECURITY-6, M-002, M-004, D19-1, CC-1 all applied. See Section 10 for full narrative and `.claude/day-20-walkthrough.md` for the plain-language write-up. |
| 22 | Seed Data — 15 Egyptian Private Events (V13 migration) | ✅ Complete | 194/194 unchanged — data-only day, no Java code changes. `V13__seed_egypt_private_events.sql` adds 1 category, 2 ORGANIZER users, 15 PUBLISHED events, 30 ticket tiers. Verified via Flyway history, direct SQL row counts, `/api/search/events` (unfiltered + category-filtered), `/api/events/{id}` detail resolution, organizer login + `/organizer/events` dashboard (both API and real browser), and public `/search` page. Full narrative in Section 10. |

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
| POST | /api/v1/auth/logout | No (graceful no-op if unauthenticated) | Denylists the caller's JWT `jti` in Redis (Day 20, Fix M-004) |

**Note (Day 20):** `/actuator/**` now requires `ADMIN` except `/actuator/health` (public). `/api/v1/auth/**` and `POST /api/v1/bookings` are rate-limited in production (`app.rate-limit.enabled=true`); the latter also requires an `Idempotency-Key` header.

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

## 10. NEXT SESSION START — DAY 27

**Current Branch:** `feat/booking-idempotency` (local — **not pushed**)

**Day 26 is complete.** `Idempotency-Key` is now honoured end to end — see the Day 26 entry at the
top of this file. Nothing has been pushed.

**First task — push and open the PR.** Then pick from the open items below.

**Two gaps the Day 26 docs audit found, both still open and both small:**

1. **`CheckInGuard` is a stub** (`CheckInGuard.java`). `evaluate()` logs and returns `true`: no
   event-date check, no organizer-ownership check. Fix 8.2's "CHECK_IN dual-guard" is therefore one
   layer, not two — any `ORGANIZER` can check in any `CONFIRMED` booking, for any event, on any
   date. The hook is wired correctly and *is* consulted on every check-in; only the body is missing.
   Closing it means injecting the repositories and comparing
   `booking.getEvent().getOrganizer().getId()` against the authenticated principal, plus the
   event-is-today window. TDD: the cross-organizer denial test goes Red first.
2. **The RabbitMQ retry/DLQ ladder is unconfigured.** The three DLQs are declared and bound, but
   no `spring.rabbitmq.listener.simple.retry.*` or `default-requeue-rejected` properties exist. Under
   Spring Boot's defaults a throwing listener **requeues in a tight loop** and never dead-letters, so
   the documented "3 attempts then DLQ" behaviour does not happen. Config-only fix; see
   `docs/Core/10_rabbitmq.md` for the exact block.

**Note on migrations:** V14 is applied. **Next is `V15__`.**

**Also still open, unchanged from Day 24 — the user's to do, not automatic:**
- Stripe CLI is **not installed**; the Stripe account still has **zero webhook endpoints**
  registered. `PaymentReconciliationService` covers the gap but the webhook is the primary path.
- Booking 562's two duplicate charges (`pi_3U6rvL…`, `pi_3U6ruj…`) have not been refunded.
- Day 21 carryovers: the 6 browser-based Critical Path smoke tests and the Railway/Vercel
  env-var audits.

**Redesign items not verified in Day 25, needing a device or a live purchase:** QR scannable
from a phone at arm's length in low light; displayed total equals the Stripe charge exactly;
all ten booking states rendering with icon + label; `prefers-reduced-motion` final-state
render (the CSS and JS guards were read, not run); a full keyboard path end-to-end through
the booking flow.

**Completed in Day 23 (Portfolio Site + 2 Production Bug Fixes, 2026-07-16):**

- While capturing screenshots for the GitHub Pages portfolio site against the live production
  deployment, a real purchase flow surfaced two independent, reproducible bugs. Both were fixed
  with a failing test written first, confirmed Red against the pre-fix code, then confirmed Green.
- **Bug 1 — reservation 500s despite committing:** `BookingService.reserveTickets()`
  (`BookingService.java`) loaded `Event` via a plain `eventRepository.findById()`, leaving
  `venue`/`category` lazy. Production's `spring.jpa.open-in-view: false` (local defaults to `true`,
  which is why this never reproduced locally) closes the Hibernate session when the transactional
  method returns, so `BookingController`'s read of `booking.getEvent().getVenue()`/`.getCategory()`
  threw `LazyInitializationException` → 500, even though the booking had already committed. Fixed
  by switching to the existing `eventRepository.findByIdWithDetails()`, which already carries the
  right fetch graph. New integration test (`BookingReservationLazyLoadingIntegrationTest`) forces
  `spring.jpa.open-in-view=false` and drives the real `BookingController` end-to-end via MockMvc.
- **Bug 2 — webhook silently failing to confirm bookings:** `WebhookService.handlePaymentSuccess()`
  published a `BookingConfirmedEvent` to RabbitMQ with no error handling, inside the same
  transaction as the `CONFIRMED` state write and the idempotency-guarding insert. A broker hiccup
  threw an unchecked `AmqpException`, and Spring's default rollback-on-unchecked-exception undid
  everything — including the idempotency row — so a Stripe retry hit the identical failure again.
  Fixed by wrapping the publish call in try/catch and logging at ERROR instead of rethrowing.
  Trade-off noted in the fix: a failed publish means that booking's ticket-generation/email event
  isn't retried automatically — a durable outbox pattern would close that gap but is out of scope.
  New test in `WebhookServiceTest` mocks the publisher to throw and asserts the booking still ends
  up `CONFIRMED` with no exception propagating.
- **Frontend:** `bookings/[id]/confirmation/page.tsx` fetched real booking data but never checked
  `state` before rendering "Booking Confirmed!" — any webhook delay showed false success. Added
  `state` to the `BookingDetails` interface; the page now branches on it: confirmed/attended shows
  the existing success UI, a terminal failure state (`PAYMENT_FAILED`/`CANCELLED`/`EXPIRED`) shows
  an explicit "Payment Not Completed" message, anything else shows "Payment Processing" with a
  manual refresh button that re-fetches in place (no full page reload).
- **Docs:** fixed a stale webhook path in `docs/Core/11_stripe_payments.md`
  (`/api/payments/webhook` → `/api/v1/payments/webhook`, 3 occurrences) that could have caused a
  future Stripe Dashboard misconfiguration if the endpoint was ever set up by copying from it.
- Verified: `./mvnw clean verify` → 196/196 tests passing (+2 — one integration test for Bug 1, one
  unit test for Bug 2), JaCoCo gate passed. Manually verified the frontend fix against a local dev
  backend running the fixed code (not production): registered a real local test account, made a
  real reservation, flipped the booking through `PAYMENT_PENDING` → `CONFIRMED` directly in
  Postgres, and confirmed the confirmation page renders each state correctly, including the
  refresh button re-fetching without a reload.
- Along the way, confirmed (by reading the actual source, not guessing) that a "random
  cancellation" observed during manual testing was `ReservationGuard`'s intentional `pagehide`
  auto-cancel (releases a held reservation immediately if the user leaves without proceeding to
  checkout) — triggered by this session's own browser-automation tooling doing a hard page
  navigation (which fires `pagehide`), not something a real user clicking in-app links would ever
  trigger. Also confirmed a "wrong user" flash on the dashboard was a hydration-timing artifact
  (`userEmail` defaulting to a hardcoded placeholder string before the Zustand store rehydrates
  from localStorage) — cosmetic only, not a session/data bug.
- **Portfolio site (`site/`):** replaced `assets/img/screenshots/08-booking-detail-qr-ticket.webp`
  — previously an honest "mid-processing" capture from before the fix — with a real capture of a
  `CONFIRMED` booking with a genuine QR code and ticket details, captured locally against the fixed
  code (production hasn't been redeployed yet). Updated the corresponding `walkthrough.html`
  caption to stop claiming the webhook hadn't landed, and instead names the two bugs this
  screenshot situation led to finding and fixing. Removed a now-redundant note under the
  `06-booking-confirmation` screenshot describing the same original issue. Fixed the nav bar's
  `.mark` logo element (`assets/css/style.css`) — it was rendering as a blank gradient square with
  no glyph; now uses `favicon.svg` as its background-image so it matches the actual "E" mark.
- Commits made with plain Conventional Commit messages, no AI-attribution trailer, per explicit
  instruction (`fix(booking)`, `fix(payment)`, `fix(frontend)`, plus this site/docs update).
  **Nothing has been pushed.** Deploying these fixes to Railway/Vercel and — if desired — redoing
  the real production purchase to recapture `06-booking-confirmation` (currently still the original
  honest mid-processing screenshot, left as-is at the user's call) are both still pending, at the
  user's discretion, not something to do automatically next session.

**Completed in Day 22b (Frontend Design Handoff, 2026-07-15):**

- Applied 3 Claude Design mockup exports (`Eventora Landing.dc.html`, `Eventora Dashboard.dc.html`,
  `Eventora Organizer Dashboard.dc.html`) to the live frontend, re-implemented as React/Tailwind
  rather than copied verbatim (the mockups use template placeholders, not real code).
- `page.tsx` (Landing): per explicit instruction, kept the hero's real photo background and fully
  functional search form (query/city/date + Search Events) — added a stats row (Events hosted /
  Tickets sold / On-time check-ins, decorative marketing copy same as the mockup itself), a
  "Become an Organizer" CTA anchoring to a new `#organizers` section, a new 3-column Feature Strip
  (static marketing copy), a new Featured Events section (first 6 real published events via the
  existing `<EventCard>`/react-query wiring, "See all events →" link to `/search`), and a new dark
  For-Organizers teaser section with a decorative (non-real-data) dashboard panel. Removed the old
  category-chip filter + full events grid — that filtering now lives solely on `/search`, which
  already has its own filter UI. Added `{ id: 6, name: "Conference" }` to `fallbackCategories`
  (id confirmed stable: V13 inserts Conference as the first row after V1–V12's categories, so it
  always lands on id 6 in any environment that runs migrations in order).
- `footer.tsx`: expanded from a single Terms/Privacy row into Product + Support columns (explicitly
  no Company column). Every link points to a real existing route — `/search`, `/#organizers`,
  `/organizer/events/new`, `/terms`, `/privacy`, plus a `mailto:` for Contact — no invented
  `/pricing` or `/help-center` pages.
- `dashboard/bookings/page.tsx` + `organizer/events/page.tsx`: visual-only polish (profile card
  gradient/avatar overlap, stat-card icon shape, a "Revenue (EGP)" legend chip on the organizer
  sales chart) plus enriched empty states (the Booking History empty state went from a bare
  sentence to icon + title + description + "Explore Events" CTA, matching the mockup and the
  pattern already used elsewhere on the same page). All react-query fetching/mutation logic in
  both files is untouched.
- Verified: `git diff` confirmed no raw hex colors were introduced (everything routes through the
  existing `--color-*` design tokens); `npm run lint` surfaced 9 pre-existing errors + 7 warnings
  across 6 files this session never touched (`dashboard/bookings/[id]/page.tsx`,
  `organizer/events/[id]/edit/page.tsx`, `TicketTierSelector.tsx`, `CartDrawer.tsx`,
  `event-card.tsx`, `bookings/[id]/confirmation/page.tsx`) — confirmed pre-existing via `git diff`,
  not introduced by this work; `npm run build` (the actual command Vercel runs on deploy) succeeds
  cleanly and is unaffected by those lint errors, since this Next.js version doesn't run ESLint as
  part of `next build`; `npm run test` (vitest) 4/4 passing, unchanged. Both dev servers were
  stopped, caches cleared (`rm -rf frontend/.next`), and restarted fresh; all three redesigned
  pages plus the shared footer were then verified in a real browser against the Day 22 seed data
  (organizer login as `events@caironightslive.eg`, `/organizer/events` showing its 8 real events,
  the landing page's Featured Events section, DOM/network inspection showing zero errors beyond a
  harmless dev-mode CSP/eval sandbox warning).
- Branch renamed from `day-22-seed-egypt-events` to `feat/seed-data-frontend-refresh` per explicit
  instruction to use feature-branch naming convention (matches the existing `feat/` precedent in
  this repo, e.g. `feat/platform-enhancements`).

**Completed in Day 22 (Seed Data — 15 Egyptian Private Events, 2026-07-15):**

- Executed `docs/Core/22_seed_data_plan_egypt_private_events.md` end-to-end: created
  `V13__seed_egypt_private_events.sql` (IMMUTABLE, next migration is V14) exactly as specified —
  1 new `Conference` category, 2 new `ORGANIZER` seed users (`events@caironightslive.eg`,
  `bookings@redsealiveent.eg`, password `EventoraSeed@2026`), 15 `PUBLISHED` events across 8
  existing private/commercial venues, and 30 ticket tiers (2 per event). Museums and
  government-run cultural/antiquities venues excluded by design.
- Verified against a real local Postgres (via `docker-compose up -d postgres redis rabbitmq
  mailhog` + Docker Desktop, since the Flyway Maven plugin couldn't resolve its dependencies
  offline — used Spring Boot's auto-run-Flyway-on-startup path instead, per the plan's documented
  fallback): Flyway history shows V13 applied successfully; row counts confirmed exact (+15
  events, +30 ticket_tiers, +2 ORGANIZER users, +1 category); `GET /api/search/events` returns all
  15 new titles, and category-filtering by the new `Conference` category returns exactly the 2
  conference events; `GET /api/events/{id}` resolves organizer/category/venue/ticketTiers
  correctly for a spot-checked event.
- Confirmed the bcrypt seed-password hash round-trips against the running app: logged in as
  `events@caironightslive.eg` via both a direct API call and a real browser session against the
  Next.js frontend, and confirmed `/organizer/events` (My Events dashboard) lists exactly that
  organizer's 8 events. Also spot-checked the public `/search` page in-browser — all 15 new events
  render with correct title/date/city/category/starting price.
- Ran the full suite: `./mvnw clean verify` → 194/194 tests passing (unchanged — this was a
  data-only migration, no Java/production code touched), JaCoCo gate passed (~83.8%
  INSTRUCTION coverage, gate-scoped — consistent with the Day 21 baseline). No test in the
  suite hardcodes an event/category/venue/ticket-tier row count, so nothing needed updating.
- Work done on a new branch (`feat/seed-data-frontend-refresh`, cut from `main`, initially named
  `day-22-seed-egypt-events` then renamed) rather than on `main` directly, per this session's
  explicit instruction.

**First task for next session — Day 24:** Push `feat/github-pages-portfolio-site` and let
Railway/Vercel redeploy the two bug fixes (nothing pushed yet as of Day 23); once live, decide
whether to redo the real production purchase to recapture `06-booking-confirmation` with the fix
in effect, or leave its honest pre-fix framing as-is. Day 21's older carryover items are still open
too (frontend must send an `Idempotency-Key` header on `POST /api/v1/bookings` before
`app.rate-limit.enabled=true` reaches production; the 6 browser-based Critical Path smoke tests and
Railway/Vercel env var audits in `PROGRESS.md`'s Day 21 row are still the user's to complete). Pick up
whatever the next `Plans/session-prompts/day-24-*.md` specifies once written.

---

## Previously — NEXT SESSION START — DAY 21

**Current Branch:** `day-20-code-quality` (local, 6 new commits — not yet pushed/merged)

**Completed in Day 20 (Code Quality + Security Hardening, 2026-07-03):**

- **SECURITY-6:** `/actuator/**` was fully `permitAll()` in `SecurityConfig.java` — anyone on the
  internet could hit `/actuator/env`, `/actuator/metrics`, etc. Split into `/actuator/health`
  (still public, needed by Docker/Railway healthchecks) and everything else now `hasRole("ADMIN")`.
- **M-002:** new `RateLimitFilter.java` — an atomic Redis Lua `INCR`+`EXPIRE` script (same
  atomicity pattern as `DistributedLockService`) limits `/api/v1/auth/**` by client IP and
  `POST /api/v1/bookings` by authenticated user (email/JWT subject), requires an
  `Idempotency-Key` header on booking creation, and excludes the Stripe webhook entirely.
  Registered as a `@Bean` inside `SecurityConfig` (never a scanned `@Component`) so it's never
  loaded inside a `@WebMvcTest` slice, and gated by `app.rate-limit.enabled` (default `false`,
  `true` in `application-prod.yml`) — this project has no dedicated `test` Spring profile, so a
  property flag was the only reliable way to keep the existing test suite unaffected.
- **M-004:** `JwtService.generateToken()` now sets a `jti` (JWT ID) claim; `revokeToken()`
  denylists it in Redis for the remainder of the token's natural lifetime; `isTokenRevoked()`
  checks it. All of this logic lives inside `JwtService` (already `@MockitoBean`'d in every
  `@WebMvcTest` slice) rather than being injected into `JwtFilter`/`AuthController` directly —
  that would have required adding a Redis mock to every single existing controller test.
  `JwtFilter` checks the denylist only after signature/expiry validation already passed, so
  public/invalid-token traffic never pays for the extra Redis lookup. New
  `POST /api/v1/auth/logout` endpoint denylists the caller's current token.
- **D19-1** (found during Day 19's k6 verification): `BookingService.reserveTickets()` never
  decremented the DB-persisted `TicketTier.availableCount` column — only the Redis inventory
  counter — so `GET /api/events/{id}` showed a stale, falsely-high seat count while holds were
  active. Fixed with a symmetric decrement/release: `reserveTickets()` now decrements the DB
  column, and `ReservationExpirationJob.expireReservedHold()` — previously relying on
  `ReleaseSeatsAction`, a Redis-release stub that does nothing — now explicitly releases and
  increments it back, mirroring the existing `cancelBooking()`/`expireStalePaymentPending()`
  pattern. A decrement-only fix (as the session plan literally described) would have permanently
  leaked inventory on every abandoned reservation.
- **CC-1 audit:** found and fixed 3 `log.error()` calls (`PaymentService.java` ×2,
  `WebhookService.java` ×1) that passed `ex.getMessage()` instead of the exception object,
  silently discarding the stack trace.
- Tests: 191/191 passing (183 baseline + 8 new — `JwtDenylistTest`, `RateLimitDisabledByDefaultTest`,
  `RateLimitEnforcedTest`, plus one new test each in `BookingServiceReserveTest` and
  `ReservationExpirationJobTest`). 82% INSTRUCTION coverage verified via `./mvnw clean verify`
  (always use `clean verify`, not an IDE's incremental single-test run — a stale/partial
  `target/jacoco.exec` merge can under-report coverage by several points). JaCoCo gate ✅ PASSED.
- Full walkthrough: `.claude/day-20-walkthrough.md`.

**First task for next session — Day 21:** Production smoke test against the live Railway
deployment, then an environment-variable audit. Carries over from Day 20: the Next.js frontend
must start sending an `Idempotency-Key` header on `POST /api/v1/bookings` before
`app.rate-limit.enabled=true` reaches production, or booking creation will fail with 400 — see
`Plans/session-prompts/day-21-*.md` for the full session prompt.

**Completed in Day 19 (Production Deploy Stabilization + Swagger/OpenAPI + k6 Load Tests, 2026-07-02):**

- **Production outage fully root-caused and fixed**, in the order discovered:
  1. `management.health.rabbit.enabled: false` + `management.health.mail.enabled: false` — Spring Boot's actuator health check was rolling RabbitMQ and SMTP connectivity into the overall `/actuator/health` status Railway uses to gate deploys, even though neither blocks core web traffic.
  2. `RabbitConnectionConfig` — Spring Boot's auto-configured RabbitMQ connection was silently defaulting to `localhost:5672` in production despite `RABBITMQ_URL` being confirmed correct inside the running container; replaced with a hand-built connection factory that reads the URI directly and fails fast if it's blank.
  3. `setAutomaticRecoveryEnabled(false)` + `setTopologyRecoveryEnabled(false)` on that same factory — after fix 2 deployed, the app started healthy but went fully unresponsive ~15-20 minutes later; root cause was the raw RabbitMQ client's own auto-recovery conflicting with Spring AMQP's separate recovery system (a documented gotcha Spring Boot's own auto-configuration normally avoids, which the manual factory had been missing).
  4. `server.port` hardcoded to `8088` in `application-prod.yml` (was `${PORT:8088}`) — after fix 3 deployed, the app started fully healthy and stayed healthy, but was still fully unreachable from the public internet (502 "connection refused"). Root cause: Railway's injected `PORT` environment variable resolved to `8080`, which won over the `8088` fallback, so the app was listening on 8080 while Railway's proxy — configured against the Dockerfile's `EXPOSE 8088` — kept knocking on 8088.
  - **Backend confirmed live and healthy** via direct `curl` to `https://backend-production-8daea.up.railway.app/actuator/health` (200, `{"status":"UP"}`) and `/api/events` (200) after fix 4 deployed.
- `pom.xml`: added `springdoc-openapi-starter-webmvc-ui:2.8.17` (pinned specifically — earlier 2.x releases have a confirmed `NoSuchMethodError` incompatibility with Spring Boot 3.5.x).
- `OpenApiConfig.java` (new): registers API title/description and a `bearerAuth` JWT security scheme for Swagger UI's Authorize button.
- `SecurityConfig.java`: permits `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/v3/api-docs.yaml`.
- All 9 REST controllers annotated with `@Tag`/`@Operation`/`@ApiResponse` (realistic 401/403/404/409 codes matching `GlobalExceptionHandler`'s actual mappings); `StripeWebhookController` marked `@Hidden` (Stripe-only callback, not client-facing). Verified live: booted the app locally, confirmed all 8 tags render in `/v3/api-docs`, webhook path correctly absent, Swagger UI reachable.
- `src/test/k6/load-test.js`: fixed wrong port (8080→8088), wrong paths (`/events`→`/api/events`), and a silent bug reading `body.content` instead of `body.data.content` that meant the event-detail sub-check never actually ran.
- `src/test/k6/booking-reservation.js` + `src/test/k6/inventory-pressure.js` (new): authenticated booking-creation load scenario and a high-concurrency oversell-pressure scenario verifying the Redis Lua floor guard degrades to clean 409s instead of overselling or 500ing.
- `PERFORMANCE.md` (new): run instructions for all 3 k6 scripts, a curl sequence for creating a published test event with ticket tiers, and placeholder result tables (not yet filled in — k6 has not been run against live Railway).
- Full walkthrough: `.claude/day-19-walkthrough.md` (gitignored, local-only — same convention as `day-18-walkthrough.md`).

**First task for next session — Day 20:** Run the 3 k6 scripts against the now-healthy Railway backend
per `PERFORMANCE.md`'s setup steps, paste results into its placeholder tables, then start Code Quality
and Security Hardening (Bucket4j rate limiting, JWT denylist, CC-1/CC-2 final audit) — see
`Plans/session-prompts/day-20-*.md` for the session prompt.

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
