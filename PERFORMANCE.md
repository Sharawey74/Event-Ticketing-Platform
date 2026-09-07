# Performance Testing — Event Ticketing Platform

## Tooling

Load testing uses [k6](https://k6.io/) (standalone CLI, not wired into CI). Scripts live in
`src/test/k6/`:

| Script | Purpose |
| :--- | :--- |
| `load-test.js` | Baseline read-path load: public `GET /api/events` + `GET /api/events/{id}`. No auth required. |
| `booking-reservation.js` | Authenticated booking creation (`POST /api/v1/bookings`) under moderate concurrency. |
| `inventory-pressure.js` | High-concurrency burst against a low-capacity tier — verifies the Redis Lua floor guard degrades to clean 409s under oversell pressure instead of overselling or 500ing. |
| `ceiling-probe.js` | **Finds a limit rather than confirming its absence.** Ramps *arrival rate* (requests/second) instead of VUs and aborts the moment a threshold breaks, so the stopping point is the measurement. Read mode (`GET /api/events`) needs no auth; write mode needs a JWT and a large tier. `PEAK_RATE` and `MAX_VUS` are env-overridable. |
| `scale-probe.js` | Holds load **constant** while the replica count changes, so the only variable is the number of instances. Run once per replica count and compare achieved throughput. |
| `capacity-ramp.js` | Staged VU ramp — **10→25→50→100→200→500→1000 over 22 min** against a weighted, realistic journey mix (40% browse, 20% search, 25% reserve, 15% check own bookings) — establishes a real capacity number for the actual live deployment (1 Railway replica, 5-connection Hikari pool), not a local dev-machine baseline. Defaults `BASE_URL` to the Railway URL, unlike the other 3 scripts. |

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

**Important caveat discovered while verifying this run — ✅ since fixed, see the note at the end of
this paragraph:** the public `GET /api/events/{id}`
response's `ticketTiers[].availableCount` field was **not a reliable way to verify no-oversell**.
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

✅ **Fixed in Day 20 (Fix D19-1).** `BookingService.reserveTickets()` now decrements the DB column
too, via an atomic conditional `UPDATE` (`TicketTierRepository.decrementAvailableCount`), and every
release path — cancel, payment-pending expiry, **and** reserved-hold expiry — increments it back
symmetrically. The API field is therefore no longer stale, and either source can be used to verify.
Reading Redis directly is still the more direct check, so the instruction above stands; the reason
for it no longer does. This paragraph is kept because the reasoning is the useful part: **verify an
invariant at the layer that enforces it, not at a layer that merely reports it.**

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

⚠️ **This result is from an earlier version of the script.** On 2026-07-04 `capacity-ramp.js`
topped out at 200 VUs (6 stages, 16 min). On 2026-07-15 the stages `500` and `1000` were appended
(commit `5aac1ea`), making it an 8-stage / 22-minute ramp. **No run of the extended script has ever
been recorded here.** So the numbers below establish a *floor* — "at least 200 VUs, clean" — not
the ceiling, and any recollection of a higher-VU run that degraded is not evidenced in this repo.

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

## What the numbers do and do not establish

**The read path now has a measured ceiling** (Day 27, below). The other three scenarios still do
not — every one of them passed, so each remains a **lower bound on capacity**, not a limit:

| Scenario | Highest load recorded | Outcome | Ceiling found? |
|---|---:|---|---|
| `ceiling-probe.js` (local read path, 1 replica) | **660 req/s** | p95 crossed 500ms | ✅ **Yes** |
| `ceiling-probe.js` (local read path, 2 replicas) | **870 req/s** | p95 crossed 500ms | ✅ **Yes** |
| `load-test.js` (local read path) | 50 VUs | p95 15.9ms, 0% errors | No |
| `booking-reservation.js` (local) | 20 VUs | p95 55.4ms, 0 5xx | No |
| `inventory-pressure.js` (local) | 100 VUs | 0 5xx, 0 oversell | No — and not applicable; it is a correctness test |
| `capacity-ramp.js` (Railway, read-only) | 200 VUs | p95 394ms, 0 failed | No |

⚠️ **The write path has never been tested above 20 VUs.** It is the path with the distributed
lock, the Lua script, the conditional `UPDATE` and two inserts — the one where the 5-connection
pool should bind hardest — and it is the largest remaining gap.

### Scope of each result

The three findings have different reach, and the distinction is load-bearing when quoting them.

| Result | Establishes | Applies to |
|---|---|---|
| **Correctness** — zero oversell, exactly 50 of 100 threads succeed, the floor guard cutting off at exactly the tier size | The inventory invariant holds under concurrent contention | Any deployment. Absent network latency, concurrent requests collide rather than arriving spread out, and a single CPU forces thread interleaving — both raise the probability of a race surfacing, making this a stricter test than production. |
| **Saturating resource** — the read path saturates CPU before the 5-connection pool, Redis or Postgres | Which resource binds first, established by capturing every container's CPU simultaneously rather than inferring from the app alone | Any deployment. It follows from the code and the pool size; absolute figures would shift on other infrastructure, the ordering would not. |
| **Throughput** — 660 / 870 req/s | Capacity within the measured envelope | This envelope, quoted as "on 1 CPU with a 5-connection pool". Real infrastructure shifts it in both directions without cancelling: network round trips lower each client's request rate, while a managed database over the network raises per-query cost. |

### Why the earlier runs could not find a ceiling

Not because the app is limitless. Because of how they generate load.

`load-test.js`, `booking-reservation.js`, `inventory-pressure.js` and `capacity-ramp.js` all ramp
**virtual users**. A VU is a closed loop: send → wait for the reply → sleep → repeat. When the
server slows from 50ms to 2s, that VU's request rate drops 40×. **The offered load falls as the
server degrades** — the test throttles its own input at exactly the moment things get interesting,
and the latency curve looks gracefully flat right up until it isn't.

`ceiling-probe.js` ramps **arrival rate** instead: k6 keeps issuing requests on schedule no matter
how slow the replies get, allocating more VUs to hold the rate. The queue actually builds, and the
cliff becomes visible.

## Day 27 — Ceiling and scaling under production-shaped constraints (2026-09-07)

First runs in this project to **find a limit** rather than confirm the absence of failure.

### Environment

Deliberately constrained to Railway's container shape via `docker-compose.perf.yml`, so the number
reflects the deployment rather than a dev laptop.

| Setting | Value | Why |
|---|---|---|
| Spring profile | `prod` | Gives the real Hikari `maximum-pool-size: 5` (local defaults to 10) |
| CPU (limit **and** reservation) | **1.0** per replica | Railway's shape. Also changes JVM behaviour — GC and ForkJoin pool are sized from `availableProcessors()` |
| Memory (limit **and** reservation) | **512 MB** | With `-XX:MaxRAMPercentage=75.0` the heap lands ~384 MB |
| Rate limiting | **disabled** via `APP_RATELIMIT_ENABLED=false` | See the warning below |
| Load generator | k6 in its own container, 2 CPU / 1 GB | So it cannot steal cores from the app it is measuring |
| Host | 8 CPUs, 7.6 GB available to Docker | Docker Desktop on Windows |
| Endpoint | `GET /api/events` | **Uncached** — `EventService.getEvents()` has no `@Cacheable`, so every request is a real query |

⚠️ **`application-prod.yml` hardcodes `app.rate-limit.enabled: true`** — a literal, not an env
placeholder. Activate the prod profile for the 5-connection pool and you also activate
`RateLimitFilter`, which caps booking creation at **5 requests/minute/user**. A load test would
flatline at ~0.08 req/s and that would be recorded as capacity. The env-var override is mandatory;
environment variables outrank `application-{profile}.yml` in Spring's property precedence.

### Results — 1 replica

| Run | Config | Ceiling | p95 | Requests | Errors | Notes |
|---|---|---:|---:|---:|---:|---|
| A | direct, `MAX_VUS=2000` | 586.6 req/s | 594.5ms | 94,332 | **0** | Peak 891/2000 VUs |
| **B** | **direct, `MAX_VUS=4000`** | **659.96 req/s** | **511.15ms** | **107,839** | **0** | **The baseline.** Peak 952/4000 VUs |
| C | via nginx, `MAX_VUS=4000` | 579.84 req/s | 637.1ms | 92,964 | **0** | The proxy hop cost ~12% |

**Run B full latency distribution:** avg 71.11ms · **median 2.40ms** · p90 87.40ms ·
**p95 511.15ms** · min 1.35ms · max 3,470.19ms · **p99 not measured**.

That spread is the signature of **queueing**: half of all requests finished in 2.4ms; a small
minority waited behind a saturated CPU. It is also why the average is useless here — 71ms describes
nobody's experience.

**Resource behaviour at the limit:** CPU climbed 4% → **~100–107%** of its 1.0 budget; memory rose
466 MiB → **511.7 MiB of 512 MiB**. A tight heap means more GC, which itself burns CPU, so the two
compound at the top end.

### Results — 2 replicas

| Run | Config | Result | p95 | Requests | Errors |
|---|---|---|---:|---:|---:|
| G | `PEAK_RATE=800` | ✅ **completed all stages, no abort** at 799.99 req/s | **9.0ms** | 140,548 | **0** |
| **H** | **`PEAK_RATE=2400`** | **ceiling 869.78 req/s** | 567.6ms | 133,383 | **0** |

Run G used only **55 VUs of 4,000** to push 800 req/s — responses came back so fast that almost
nothing was ever in flight. Run B needed **952 VUs** for less traffic. That contrast is the
clearest single indicator of headroom in this whole exercise.

### Scaling

| Replicas | Ceiling | p95 at abort | Per-replica |
|---|---:|---:|---:|
| 1 | 660 req/s | 511ms | 660 |
| 2 | 870 req/s | 568ms | 435 |

**Measured factor: 1.32×** — sub-linear.

The stronger and cleaner comparison is latency: at **800 req/s two replicas held p95 = 9ms**, while
one replica at *lower* load (660) was at **511ms**.

### Bottleneck identification

Full container CPU captured during a 2-replica ceiling run:

| Container | Peak CPU | Budget | Verdict |
|---|---:|---:|---|
| **app-1** | **105.40%** | 100% | **Saturated** |
| **app-2** | **105.23%** | 100% | **Saturated** |
| postgres | 106.34% | uncapped | ~1 core, busy but free to take more |
| k6 | 68.43% | 200% | Not the constraint |
| nginx (`lb`) | 0.00% | — | Confirmed out of the path |
| redis | ~0.5% | uncapped | Negligible — not on the read path |
| **Total observed** | **~370%** | 800% | **Host not saturated** |

✅ **The read path is CPU-bound, not database-bound.** The 5-connection pool never became the
limit; both replicas saturated their own CPU while the pool, Redis and the host all had headroom.

⚠️ **Open question — why scaling is sub-linear.** Each replica delivered 435 req/s when paired
versus 660 alone, a ~34% per-replica efficiency loss. Ruled out by the capture above: k6, the host,
nginx, and the connection pool. Remaining hypothesis, **unproven**: Docker Desktop's virtualised
network stack — on Windows, packet processing happens in the VM kernel and is attributed to no
container, so it is invisible in `docker stats`. At 870 req/s across ~1,331 connections that is
substantial hidden work. If correct it is an artifact of Docker Desktop, not of this application.
Confirming it requires running k6 on a separate host.

⚠️ **`rabbitmq` showed sporadic CPU spikes** (204%, 323%, 249%, 257%) **uncorrelated with load** —
they appear while everything else is idle. Most likely its internal management processes or a
`docker stats` sampling artifact. Not explained, and not treated as meaningful.

### One run discarded

An intended 2-replica run was **invalid** and its numbers must not be used. Two independent causes:

1. **`docker compose run` deleted the second replica** before starting (visible in its output as
   `Container ...app-2  Removed`). Compose reconciles the project to the scale declared in the
   file unless `--no-deps` is passed, so the run measured 1 replica while claiming 2.
2. **nginx produced 223 5xx** (`connection reset by peer`, `EOF`) while the application's own p95
   was healthy at 483ms. Variable `proxy_pass` — required for per-request DNS re-resolution —
   forbids upstream keepalive in open-source nginx, so every request opened a fresh TCP connection
   and the proxy exhausted itself at ~2,000 VUs.

nginx was consequently removed from the default path in favour of Docker's own DNS round-robin,
which also makes results directly comparable to the pre-proxy baseline.

### Methodology notes worth keeping

1. **A passing load test may prove nothing.** VU-based ramping throttles its own input as the
   server degrades. Use arrival-rate with `abortOnFail` to find a limit.
2. **Check the rig before believing the result.** `dropped_iterations` and peak-VU headroom tell
   you whether you measured the app or the load generator. `ceiling-probe.js` refuses its own
   output when it was starved.
3. **Warm the JVM.** Identical 150 req/s load, 2 replicas: a 44-second-old replica gave
   **p95 831.6ms**; warm, the same run gave **125.1ms**. A 6.6× difference from JIT alone.
4. **`docker compose run` needs `--no-deps`** or it destroys scaled replicas.
5. **Watch every container, not just the app.** One capture ruled out four candidate bottlenecks.

### Terminology that causes wrong claims

Four distinctions worth stating, because collapsing any of them turns a correct measurement into a
false claim:

| Not the same | Why it matters here |
|---|---|
| **VUs ≠ requests/second** | A VU is one looping client, not one request. Run G pushed 800 req/s with 55 VUs; Run B pushed less with 952. Quoting a VU count as throughput is meaningless without the latency alongside it. |
| **VUs ≠ users** | A VU sends requests back-to-back with no think time. One VU is closer to several impatient real users than to one. |
| **iterations ≠ requests** | The Railway ramp recorded 54,290 iterations for 32,577 requests, because no-op journeys still complete an iteration. |
| **average ≠ p95** | Run B's average was 71ms and its median 2.40ms, while p95 was 511ms. The average described nobody's experience. |
| **capacity ≠ scalability** | Capacity is what one instance handles; scalability is whether adding instances multiplies it. A good score on one says nothing about the other. |

## Known limitations

- **P99 latency is not measured** in any run — k6's default summary reports p90 and p95 only.
  Add `summaryTrendStats: ['avg','min','med','p(90)','p(95)','p(99)','max']` to capture it.
- **Hikari pool metrics are not measured.** `/actuator/**` is ADMIN-only under the prod profile
  (Fix SECURITY-6), so `hikaricp.connections.pending` returns 401. Container CPU was used to
  distinguish compute-bound from pool-bound instead.
- **The write path has never been load-tested above 20 VUs**, and never at all under the
  constrained prod-shaped environment.
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
