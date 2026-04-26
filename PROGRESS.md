# PROGRESS.md — Event Ticketing Platform
## Implementation Status Tracker

> **Started:** Day 0 (not yet begun) | **Target End:** Day 21

---

## Day-by-Day Status

| Day | Theme | Status | Tests | Notes |
|-----|-------|--------|-------|-------|
| 0 | Pre-flight Setup | ✅ Complete | — | Run constitution, verify scripts |
| 1 | Project Init + Entities + Migrations | ✅ Complete | Passing | |
| 2 | Event Domain + Auth (JWT) | ✅ Complete | 20/20 passing ✅ | Event/Auth services, endpoints, and Application Context validated |
| 3 | Venue + Category + Search | ✅ Complete | 56/56 passing ✅ (2 Docker/Testcontainers skipped — pre-existing, no Docker Desktop) | VenueService, CategoryService, EventSearchService, controllers, migrations V8+V9, TestSecurityConfig, @PreAuthorize enforced |
| 4 | Next.js Frontend + Home Page | ✅ Complete | Passing | Scaffolded Next.js, added standard search + details, lint and build green |
| 5 | Inventory (Redis + Lua) + RabbitMQ Config | ✅ Complete | 63/63 passing | Implemented Lua floor guard, Warmup Health Indicator, Redis caching, RabbitMQ DLQs |
| 6 | N+1 Fixes + Integration Tests | ✅ Complete | 68/68 passing | @EntityGraph on EventRepo/BookingRepo, EventIntegrationTest (4 tests: create/update/publish/search), BookingIntegrationTest, k6 baseline, README updated |
| 7 | Week 1 Cleanup + Docker Compose | ⬜ Not Started | — | |
| 8 | Booking State Machine | ⬜ Not Started | — | |
| 9 | Stripe Checkout + Webhook | ⬜ Not Started | — | |
| 10 | RabbitMQ Consumers + Notifications | ⬜ Not Started | — | |
| 11 | Pricing Engine + Waitlist | ⬜ Not Started | — | |
| 12 | Refund Logic + Concurrency Polish | ⬜ Not Started | — | |
| 13 | Frontend Event Detail + Booking Flow | ⬜ Not Started | — | |
| 14 | Frontend User Dashboard + QR Display | ⬜ Not Started | — | |
| 15 | Frontend Organizer Dashboard | ⬜ Not Started | — | |
| 16 | Backend Test Coverage Push (80%+) | ⬜ Not Started | — | |
| 17 | Docker Multi-stage + Compose Polish | ⬜ Not Started | — | |
| 18 | CI/CD Pipeline (GitHub Actions) | ⬜ Not Started | — | |
| 19 | Performance + k6 Load Tests | ⬜ Not Started | — | |
| 20 | Code Quality + Technical Debt | ⬜ Not Started | — | |
| 21 | Final Cleanup + Deploy to Railway + Vercel | ⬜ Not Started | — | |

---

## Overlay Fixes Status

| Fix ID | Severity | Day | Applied | Notes |
|--------|----------|-----|---------|-------|
| Fix 1.1 | CRITICAL | 1 | ✅ | Instant vs LocalDateTime on all entities |
| Fix 1.2 | IMPORTANT | 1 | ✅ | ENUM type for user_role in SQL |
| Fix 1.3 | GOOD | 1 | ✅ | deleted_at TIMESTAMPTZ on bookings table |
| Fix 2.1 | IMPORTANT | 2 | ✅ | Applied on EventService, AuthService, UserDetailsServiceImpl |
| Fix 2.2 | IMPORTANT | 2 | ✅ | Applied in all new services/controllers/security classes |
| Fix 5.1 | CRITICAL | 5 | ✅ | Lua floor guard in InventoryService |
| Fix 5.2 | IMPORTANT | 5 | ✅ | InventoryWarmupHealthIndicator |
| Fix 7.1 | IMPORTANT | 7 | ⬜ | @Version on Booking and TicketTier |
| Fix 8.1 | CRITICAL | 8 | ⬜ | TOCTOU double-check inside lock |
| Fix 8.2 | IMPORTANT | 8 | ⬜ | CheckInGuard two-layer protection |
| Fix 8.3 | IMPORTANT | 8 | ⬜ | ExpiryJob distributed lock |
| Fix 8.4 | IMPORTANT | 8 | ⬜ | Booking.expiresAt uses BusinessConstants |
| Fix 9.1 | CRITICAL | 9 | ⬜ | StripeWebhookController NOT @Transactional |
| Fix 9.2 | CRITICAL | 9 | ⬜ | DataIntegrityViolationException idempotency |
| Fix 10.1 | IMPORTANT | 10 | ⬜ | DLQ listeners declared |
| Fix 10.2 | IMPORTANT | 10 | ✅ | Async QR generation via queue (Queue configured in Day 5) |
| Fix 11.1 | IMPORTANT | 8 | ⬜ | CANCELLED state in state machine |
| Fix 11.2 | IMPORTANT | 11 | ⬜ | RELEASE event / AVAILABLE state clarified |
| Fix 12.1 | GOOD | 12 | ⬜ | Refund days calculated with ChronoUnit |
| Fix 16.1 | CRITICAL | 16 | ⬜ | 80% test coverage gate before deploy |
| Fix CC-1 | GOOD | All | ✅ | X-Correlation-ID in all log statements |
| Fix CC-2 | IMPORTANT | All | ✅ | No magic numbers — BusinessConstants only |
| Fix PW3-1 | CRITICAL | 1/2 | ✅ | Stripe account + CLI installed |

---

## Key Metrics (fill in each day)

| Metric | Current | Target |
|--------|---------|--------|
| `./mvnw test` passing | 68 / 68 Tests Passing | 100% |
| Test coverage | N/A | 80%+ |
| Active @Autowired usages | 0 | 0 |
| Active LocalDateTime usages | 0 | 0 |
| Magic numbers in code | 0 | 0 |

---

## Update Instructions

After each session, update this file:
- Change ⬜ to ✅ for completed days and applied fixes
- Fill in the Metrics table
- Note any blockers in the Notes column
