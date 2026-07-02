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
curl -X POST $BASE_URL/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"loadtest-organizer@example.com","password":"Password123!","fullName":"Load Test Organizer","role":"ORGANIZER"}'

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

# 7. Register/log in a separate ATTENDEE account to use as AUTH_TOKEN for the booking scripts
#    (an organizer can also book their own event, but a dedicated attendee account is cleaner)
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

### Day 19 — Railway baseline (`load-test.js`)

| Metric | Value |
| :--- | :--- |
| p95 latency | _pending — paste from k6 run summary_ |
| Error rate | _pending — paste from k6 run summary_ |
| Throughput | _pending — paste from k6 run summary_ |

### Booking reservation scenario (`booking-reservation.js`)

| Metric | Value |
| :--- | :--- |
| p95 latency | _pending — paste from k6 run summary_ |
| 200 rate | _pending — paste from k6 run summary_ |
| 409 rate (expected under load) | _pending — paste from k6 run summary_ |
| 5xx rate (`booking_server_errors`) | _pending — paste from k6 run summary_ |

### Inventory pressure scenario (`inventory-pressure.js`)

| Metric | Value |
| :--- | :--- |
| Total requests | _pending — paste from k6 run summary_ |
| 200 rate (successful reservations) | _pending — paste from k6 run summary_ |
| 409 rate (sold out — expected, PASS) | _pending — paste from k6 run summary_ |
| 5xx rate (`booking_server_errors` — must be 0) | _pending — paste from k6 run summary_ |
| Final tier availability (confirm no oversell) | _pending — query DB/Redis after run_ |

## Known limitations

- k6 is not wired into CI (`.github/workflows/main.yml` is test-only; no load-test job).
- Scenarios 2 and 3 require manually creating a test event/tier beforehand and cleaning it up
  (or leaving it as permanent fixture data) afterward — there is no automated teardown.
- Running scenario 3 against production will permanently consume the low-capacity tier's
  inventory; use a dedicated, clearly-named test event, not a real event.
