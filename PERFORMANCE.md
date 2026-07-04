# Performance Testing — Event Ticketing Platform

## Tooling

Load testing uses [k6](https://k6.io/) (standalone CLI, not wired into CI). Scripts live in
`src/test/k6/`:

| Script | Purpose |
| :--- | :--- |
| `load-test.js` | Baseline read-path load: public `GET /api/events` + `GET /api/events/{id}`. No auth required. |
| `booking-reservation.js` | Authenticated booking creation (`POST /api/v1/bookings`) under moderate concurrency. |
| `inventory-pressure.js` | High-concurrency burst against a low-capacity tier — verifies the Redis Lua floor guard degrades to clean 409s under oversell pressure instead of overselling or 500ing. |
| `capacity-ramp.js` | Staged VU ramp (10→25→50→100→200) against a weighted, realistic journey mix (40% browse, 20% search, 25% reserve, 15% check own bookings) — establishes a real capacity number for the actual live deployment (1 Railway replica, 5-connection Hikari pool), not a local dev-machine baseline. Defaults `BASE_URL` to the Railway URL, unlike the other 3 scripts. |

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

### 4. Capacity ramp against live Railway (`capacity-ramp.js`)

Unlike scenarios 1-3, this defaults `BASE_URL` to the live Railway URL — its purpose is a real
capacity number for the actual deployment (1 replica, 5-connection Hikari pool), not a local
dev-machine number. `AUTH_TOKEN`/`EVENT_ID`/`TIER_ID` are optional: without them the reserve and
my-bookings journeys silently no-op and only the browse/search journeys run.

```bash
k6 run \
  --env AUTH_TOKEN=<jwt> \
  --env EVENT_ID=<id> \
  --env TIER_ID=<id> \
  src/test/k6/capacity-ramp.js
```

**While this is running**, poll RabbitMQ queue depth so a backlog on the notification/ticket-
generation queues doesn't go unnoticed just because the HTTP response stayed fast (consumer
concurrency is unset — Spring Boot's default, effectively 1 consumer per queue). Either watch
CloudAMQP's management dashboard for the 3 queues, or poll its HTTP management API directly:

```bash
curl -s -u <cloudamqp-user>:<cloudamqp-password> \
  "https://<cloudamqp-host>/api/queues" | jq '.[] | {name, messages, messages_ready}'
```

Record the peak `messages_ready` seen for each queue during the run alongside the k6 results
below — there is no automated capture for this yet (see Known limitations).

## Prerequisite setup for scenarios 2-4

All 3 booking-related scenarios need a real published event with a real ticket tier, and a real
JWT. Run this sequence against whichever `BASE_URL` you're testing:

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

### Capacity Ramp — Railway (`capacity-ramp.js`)

Run 2026-07-04 against the live Railway backend, full 6-stage ramp (10→25→50→100→200→0 VUs over
16m00s). **Run scope was deliberately read-only**: no `AUTH_TOKEN`/`EVENT_ID`/`TIER_ID` were
supplied, so the `reserve` and `my-bookings` journeys silently no-op per the script's design —
only `browse` (40%) and `search` (20% → renormalized to 100% between the two active journeys)
actually issued requests. This was a deliberate choice to get a real capacity number for the read
path without creating load-test bookings or exercising the production rate limiter against a
single-replica live deployment. A future run with real credentials would exercise all 4 journeys.

**Aggregate results (whole run, all 200 max VUs):**

| Metric | Value |
| :--- | :--- |
| Total requests | 32,577 (33.89 req/s average over 16m01s) |
| Total iterations | 54,290 (56.48/s — higher than request count because the no-op reserve/my-bookings journeys still count as completed iterations) |
| `http_req_failed` | **0.00%** (0 of 32,577) |
| `checks_succeeded` | **100.00%** (32,577 of 32,577) |
| `capacity_server_errors` (5xx) | **0** — threshold `count==0` ✓ passed |
| Latency — browse (`p95` threshold `<500ms`) | avg 258.3ms · p90 340.4ms · **p95 394.0ms** ✓ passed |
| Latency — search (`p95` threshold `<500ms`) | avg 260.7ms · p90 344.7ms · **p95 394.5ms** ✓ passed |
| Latency — reserve / my-bookings | N/A this run — no-op (see scope note above); thresholds trivially passed at `0s` |
| Overall latency | avg 259.1ms · min 200.1ms · median 219.3ms · max 3.51s · p95 394.1ms |
| Peak concurrent VUs reached | 200 (full ramp target reached and sustained) |
| Peak RabbitMQ queue depth | _not captured this run — see Known limitations_ |

**What this shows:** the read path (event browsing + search) holds a sub-400ms p95 all the way up
to 200 concurrent virtual users against the live single-replica Railway deployment, with zero
failed requests and zero 5xx errors across the full 16-minute ramp. This is markedly higher
latency than the local baseline (p95 15.9ms) — expected, since this traffic crosses the public
internet to a real deployment rather than `localhost`, and the local baseline used a lower peak
VU count (50, not 200).

**Not captured in this run** (would require a follow-up run with real credentials):

- A true per-VU-stage breakdown (10 / 25 / 50 / 100 / 200 taken separately) — k6's console summary
  reports run-wide aggregates by default; per-stage numbers would need the script's requests
  explicitly tagged by stage and analyzed from the raw JSON output (`--out json=...`), which this
  run did not enable.
- Booking-creation (`reserve`) and authenticated (`my-bookings`) journey numbers at scale.

## Known limitations

- k6 is not wired into CI (`.github/workflows/main.yml` is test-only; no load-test job).
- `capacity-ramp.js` has been run against live Railway (2026-07-04, read-only scope — browse/search
  only, see results above), but RabbitMQ queue-depth was not captured during that run — it's a
  manual dashboard/curl step (see "How to run" §4), not an automated metric, and there is no
  Actuator/Micrometer RabbitMQ integration in this project (flagged as a Phase 1B stretch item).
  A follow-up run with real credentials is still needed to get `reserve`/`my-bookings` numbers at
  scale, and per-VU-stage (rather than whole-run) latency breakdowns.
- Scenarios 2 and 3 require manually creating a test event/tier beforehand and cleaning it up
  (or leaving it as permanent fixture data) afterward — there is no automated teardown.
- Running scenario 3 against production will permanently consume the low-capacity tier's
  inventory; use a dedicated, clearly-named test event, not a real event.
