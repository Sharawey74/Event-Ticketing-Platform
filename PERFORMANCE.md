# Performance Testing — Event Ticketing Platform

## Tooling

Load testing uses [k6](https://k6.io/) (standalone CLI, not wired into CI). Scripts live in
`src/test/k6/`:

| Script | Purpose |
| :--- | :--- |
| `load-test.js` | Baseline read-path load: public `GET /api/events` + `GET /api/events/{id}`. No auth required. |
| `booking-reservation.js` | Authenticated booking creation (`POST /api/v1/bookings`) under moderate concurrency. |
| `inventory-pressure.js` | High-concurrency burst against a low-capacity tier — verifies the Redis Lua floor guard degrades to clean 409s under oversell pressure instead of overselling or 500ing. |

## How to run

Install k6: https://k6.io/docs/get-started/installation/

### 1. Baseline read-path load (`load-test.js`)

No auth or setup required — hits public endpoints only.

```bash
k6 run --env BASE_URL=http://localhost:8088 src/test/k6/load-test.js
```

Against Railway production:

```bash
k6 run --env BASE_URL=https://backend-production-8daea.up.railway.app src/test/k6/load-test.js
```

### 2. Booking reservation load (`booking-reservation.js`)

Requires a real `AUTH_TOKEN`, `EVENT_ID`, and `TIER_ID` — see **Prerequisite setup** below.

```bash
k6 run \
  --env BASE_URL=http://localhost:8088 \
  --env AUTH_TOKEN=<jwt> \
  --env EVENT_ID=<id> \
  --env TIER_ID=<id> \
  src/test/k6/booking-reservation.js
```

### 3. Inventory oversell pressure (`inventory-pressure.js`)

Same env vars as above, but point `TIER_ID` at a tier with a deliberately small
`totalCapacity` (e.g. 5-10) so the burst reliably exhausts it.

```bash
k6 run \
  --env BASE_URL=http://localhost:8088 \
  --env AUTH_TOKEN=<jwt> \
  --env EVENT_ID=<id> \
  --env TIER_ID=<low-capacity-tier-id> \
  src/test/k6/inventory-pressure.js
```

## Prerequisite setup for scenarios 2 and 3

Both booking scenarios need a real published event with a real ticket tier, and a real JWT.
Run this sequence against whichever `BASE_URL` you're testing:

```bash
# 1. Register an organizer (or reuse an existing one)
#    Role must be USER, ORGANIZER, or ADMIN (see com.ticketing.user.model.Role) — ATTENDEE is not a valid value
curl -X POST $BASE_URL/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"loadtest-organizer@example.com","password":"Password123!","firstName":"Load","lastName":"Organizer","role":"ORGANIZER"}'

# 2. Log in — copy the token from the response
curl -X POST $BASE_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"loadtest-organizer@example.com","password":"Password123!"}'

# 3. Look up a seeded categoryId / venueId (from Flyway V9__seed_data.sql — present in every environment)
curl $BASE_URL/api/categories
curl $BASE_URL/api/venues

# 4. Create the event — note ticketTiers, NOT a "status" field (server defaults to DRAFT)
curl -X POST $BASE_URL/api/events \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <organizer-jwt>" \
  -d '{
    "title": "Load Test Event",
    "description": "k6 load test fixture",
    "categoryId": 1,
    "venueId": 1,
    "startDate": "2026-12-01T18:00:00Z",
    "endDate": "2026-12-01T22:00:00Z",
    "ticketTiers": [
      {"tierName": "General", "basePrice": 10.00, "totalCapacity": 500, "maxPerBooking": 10},
      {"tierName": "LowCapacity", "basePrice": 5.00, "totalCapacity": 8, "maxPerBooking": 10}
    ]
  }'

# 5. Publish the event — BookingService rejects bookings on non-PUBLISHED events
curl -X POST $BASE_URL/api/events/<eventId>/publish \
  -H "Authorization: Bearer <organizer-jwt>"

# 6. Read back the tier IDs
curl $BASE_URL/api/events/<eventId>

# 7. Register/log in a separate attendee account (role: "USER") to use as AUTH_TOKEN for the
#    booking scripts (an organizer can also book their own event, but a dedicated account is cleaner)
curl -X POST $BASE_URL/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"loadtest-attendee@example.com","password":"Password123!","firstName":"Load","lastName":"Attendee","role":"USER"}'

curl -X POST $BASE_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"loadtest-attendee@example.com","password":"Password123!"}'
```

Use the `"General"` tier's id for `booking-reservation.js` and the `"LowCapacity"` tier's id
for `inventory-pressure.js`.

## Results

### Day 6 baseline

_Never recorded — no such data exists in repo history. This row is a placeholder only; do not
treat any prior "baseline" claim as real without a corresponding k6 summary in this file._

| Metric | Value |
| :--- | :--- |
| p95 latency | _pending — paste from k6 run summary_ |
| Error rate | _pending — paste from k6 run summary_ |
| Throughput | _pending — paste from k6 run summary_ |

### Day 19 — Local baseline (`load-test.js`)

Run against `http://localhost:8088` (Docker Compose `postgres`/`redis`/`rabbitmq` + `spring-boot:run`),
50 VUs over 2 minutes (30s ramp-up, 1m steady, 30s ramp-down).

| Metric | Value |
| :--- | :--- |
| p95 latency | 15.92ms |
| p90 latency | 13.1ms |
| avg latency | 7.6ms |
| min / max latency | 1.64ms / 66.37ms |
| Error rate (`http_req_failed`) | 0.00% (0 / 8930) |
| Checks passed | 100.00% (13395 / 13395) — includes `events status is 200`, `events returned fast`, `event detail status is 200` |
| Throughput | 74.41 req/s (8930 requests over 2m00s), 37.21 iterations/s |
| Thresholds | `p(95)<500` ✓ pass, `rate<0.01` ✓ pass |

### Day 19 — Railway baseline (`load-test.js`)

| Metric | Value |
| :--- | :--- |
| p95 latency | _pending — paste from k6 run summary_ |
| Error rate | _pending — paste from k6 run summary_ |
| Throughput | _pending — paste from k6 run summary_ |

### Booking reservation scenario (`booking-reservation.js`)

Run against local `http://localhost:8088`, `TIER_ID=26` ("General" tier, `totalCapacity=500`),
20 VUs over 2 minutes (30s ramp-up, 1m steady, 30s ramp-down, 1 booking attempt/VU/second).

| Metric | Value |
| :--- | :--- |
| Total requests | 1766 (14.69 req/s over 2m00.2s) |
| p95 / avg latency | 55.42ms / 31.9ms — threshold `p(95)<800` ✓ passed |
| 200 rate (successful reservations) | 500 requests (28.32%) — computed as `1766 - http_req_failed count`, verified independently via Redis below |
| 409 rate (sold out — expected once capacity exhausted) | 1266 requests (71.68%) |
| 5xx rate (`booking_server_errors` — must be 0) | 0 — threshold `count==0` ✓ passed |
| `check()` pass rate ("200 or 409") | 100.00% (1766/1766) |
| Final tier availability (verified via Redis, **not** the stale API field — see caveat below) | `inventory:tier:26:available` = **0** (started at 500) — exactly 500 succeeded, floor guard held, zero oversell |

**What this run shows:** 20 concurrent users each booking once per second for 2 minutes generates
up to ~2,400 attempts against a 500-seat tier — comfortably enough to exhaust it partway through
the run. The important result isn't the exact 200/409 split (that depends on VU count vs. tier
size, which is somewhat arbitrary for this scenario) — it's that **zero requests failed with a
5xx** and the floor guard cut off at exactly 500, not one seat more.

### Inventory pressure scenario (`inventory-pressure.js`)

Run against local `http://localhost:8088`, `TIER_ID=27` ("LowCapacity" tier, `totalCapacity=8`),
burst to 100 VUs over 40s (per the script's `10s→100 / 20s hold / 10s ramp-down` stages).

**Important caveat discovered while verifying this run:** the public `GET /api/events/{id}`
response's `ticketTiers[].availableCount` field is **not a reliable way to verify no-oversell**.
Tracing `BookingService.reserveTickets()` shows it only decrements Redis inventory
(`InventoryService.reserveSeat()`); the database's `ticket_tiers.available_count` column is only
ever *incremented* — on cancel (`BookingService.cancelBooking()`) or expiry
(`ReservationExpirationJob`) — and is never decremented when a reservation is made.
`EventService.java` maps the API response directly from that DB column with no live Redis merge,
so the number shown to a browsing user does not reflect currently-held (RESERVED) inventory. This
is a real gap worth fixing separately from load testing, but it means the *correct* way to verify
"no oversell" is to read Redis directly, not the API:

```bash
docker exec <redis-container-name> redis-cli GET "inventory:tier:<tierId>:available"
```

**Run 1 (fresh tier, 8 seats available):**

| Metric | Value |
| :--- | :--- |
| Total requests | 10433 (259.9 req/s over 40.1s) |
| 200 rate (successful reservations) | 8 requests (0.08%) — computed as `10433 - http_req_failed count`, since k6's default `http_req_failed` metric classifies non-2xx (i.e. the 409s) as "failed" even though they're the expected/desired outcome here |
| 409 rate (sold out — expected, PASS) | 10425 requests (99.92%) |
| 5xx rate (`booking_server_errors` — must be 0) | 0 — threshold `count==0` ✓ passed |
| `check()` pass rate ("200 or 409") | 100.00% (10433/10433) |
| p95 / avg latency | 290.13ms / 86.91ms (elevated vs. baseline, expected under a 100-VU burst) |
| Final tier availability (verified via Redis, **not** the API) | `inventory:tier:27:available` = **0** (started at 8) — exactly 8 succeeded, floor guard held, zero oversell |

**Run 2 (same tier, already exhausted from Run 1):**

| Metric | Value |
| :--- | :--- |
| Total requests | 10844 (270.2 req/s over 40.1s) |
| 200 rate | 0 requests (0.00%) — tier already at 0 remaining before this run started |
| 409 rate | 10844 requests (100.00%) |
| 5xx rate (`booking_server_errors` — must be 0) | 0 — threshold `count==0` ✓ passed |
| `check()` pass rate | 100.00% (10844/10844) |

**Conclusion:** across both runs (21,277 total requests, 100 concurrent VUs), the Redis Lua floor
guard never allowed more than the tier's true capacity (8) to succeed, never returned a 500, and
correctly degraded to clean 409s both while draining the last few seats (Run 1) and once fully
sold out (Run 2).

## Known limitations

- k6 is not wired into CI (`.github/workflows/main.yml` is test-only; no load-test job).
- Scenarios 2 and 3 require manually creating a test event/tier beforehand and cleaning it up
  (or leaving it as permanent fixture data) afterward — there is no automated teardown.
- Running scenario 3 against production will permanently consume the low-capacity tier's
  inventory; use a dedicated, clearly-named test event, not a real event.
