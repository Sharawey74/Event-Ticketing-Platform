# Day 5 — Session Prompt
**Date:** Wednesday, April 8, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 5 — Inventory Service (Redis + Lua) + RabbitMQ Config.
Feature: inventory-redis

Active fixes today:
- Fix 5.1 — CRITICAL: reserveSeat() MUST use Lua floor guard script, never plain DECR
- Fix 5.2 — IMPORTANT: InventoryWarmupHealthIndicator blocks /actuator/health until cache warm

Pre-conditions confirmed:
- Day 4 complete: Next.js home page rendering real event data ✅
- All backend services from Week 1 working ✅
- Docker Desktop is RUNNING ✅ (required for Testcontainers)

TDD MANDATORY — Start with tests FIRST (Red phase):
Write InventoryServiceTest with real Redis via Testcontainers BEFORE any service code:
  @Container GenericContainer redis = new GenericContainer<>("redis:7").withExposedPorts(6379)
  reserveSeat_whenSufficientInventory_shouldDecrementCount()
  reserveSeat_whenInsufficientInventory_shouldReturnFalse()
  releaseSeat_shouldIncrementCount()
  concurrentReservations_100threads_50seats_exactlyFiftySucceed()  ← THE critical gate test

Run ./mvnw test -Dtest=InventoryServiceTest — ALL 4 must FAIL before coding.
Do NOT write InventoryService until all 4 tests are red.

Non-negotiable rules:
- Lua atomic check+decrement (never plain DECR or two-step SETNX+EXPIRE)
- BusinessConstants.RESERVATION_TTL_SECONDS (never raw 300)
- @RequiredArgsConstructor + private final
- Testcontainers image: redis:7 (matches docker-compose version)

Start with: write the full InventoryServiceTest class (all 4 methods, bodies failing).
State all test signatures before writing any InventoryService code.
```

---

## Context Briefing

**What we're building today:**
Day 5 introduces the two most critical infrastructure services: `InventoryService` (Redis-backed, Lua-guarded) and `RabbitMQConfig` (infrastructure declaration only — consumers come on Day 10). This is the first day where concurrency safety becomes a hard requirement.

**Why this day is high risk:**
The Lua floor guard (Fix 5.1) is the most subtle bug in the entire plan. Without it, `DECR` on a Redis key will go below zero, causing oversell. The test `concurrentReservations_shouldNotOversell()` is the proving test — it must pass before moving on.

**Pre-conditions from Day 4:**
- Next.js home page rendering real data ✅
- All Week 1 backend services working ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 5
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`
- **Redis/RabbitMQ reference:** `Plans/Text/Phase1A_Sections 6,7,8,9_ImplementationGuides.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
|--------|----------|-----------------|
| **Fix 5.1** | 🔴 CRITICAL | The `reserveSeat()` method MUST use a Lua script for atomic check-and-decrement. A plain `DECR` is NOT safe — it decrements even when the count is already 0 or negative, causing oversell. |
| **Fix 5.2** | 🟡 IMPORTANT | Create `InventoryWarmupHealthIndicator` — blocks the `/actuator/health` endpoint from returning UP until the Redis inventory cache has been loaded. Prevents reservations on a cold (empty) cache. |

---

## Tasks (In Order)

### Morning (1 hr) — Research
- Read Redis distributed lock documentation (focus on atomic `SET key value NX EX` — the TWO-command pattern `SETNX + EXPIRE` is a race condition)
- Understand why Lua scripts are needed for atomic read + decrement

### Afternoon (3.5 hrs) — InventoryService + Redis

#### STEP 1 — Write InventoryService Tests FIRST (Red — TDD)
Before writing any service code, write all 4 tests. They will all fail (red) since
the service doesn't exist yet. Use @Testcontainers with a real Redis container:
```java
@Testcontainers
class InventoryServiceTest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    reserveSeat_whenSufficientInventory_shouldDecrementCount()
    reserveSeat_whenInsufficientInventory_shouldReturnFalse()   // expects false — not -1
    releaseSeat_shouldIncrementCount()
    concurrentReservations_100threads_50seats_exactlyFiftySucceed()
    //   ^^ This is THE critical test. 100 threads. 50 seats. Count successes.
    //      assertThat(successCount.get()).isEqualTo(50)  -- never 51, never 49.
}
```
Run: `./mvnw test -pl . -Dtest=InventoryServiceTest` — confirm all 4 FAIL before coding.

#### STEP 2 — Implement InventoryService (Green — TDD, Fix 5.1 CRITICAL)
Now write the service to make the tests pass:
```java
// reserveSeat(Long tierId, Long userId, int quantity)
// MUST use this Lua script — plain DECR is NOT acceptable:
String luaScript = """
    local current = tonumber(redis.call('GET', KEYS[1]))
    if current == nil then return -2 end          -- key missing = cache not warm
    if current < tonumber(ARGV[1]) then return -1 end  -- insufficient stock
    return redis.call('DECRBY', KEYS[1], ARGV[1])      -- atomic decrement
""";
// Key: "inventory:tier:{tierId}:available"
// Returns: new count (>= 0), -1 (insufficient), -2 (key missing)

// getAvailableCount(Long tierId) — reads from Redis; falls back to DB on cache miss
// releaseSeat(Long tierId, int quantity) — INCRBY (atomic increment)
// commitReservation — no-op (DB is source of truth)
```
Run tests again — all 4 must now pass (green). The concurrency test is the gate.
Do NOT move forward until `concurrentReservations_100threads_50seats_exactlyFiftySucceed()` passes.

#### STEP 3 — Redis Startup Warm-up (Fix 5.2)
- On `@PostConstruct`, load all tier availability from DB into Redis
- Create `InventoryWarmupHealthIndicator implements HealthIndicator` — returns `DOWN` until warm-up completes

#### STEP 4 — CacheService for Events
- `@Cacheable` on `getEventById` — key: `event:{id}`, TTL: 10 min
- `@CacheEvict` on `updateEvent` and `deleteEvent`
- `@CacheEvict(allEntries = true)` on `publishEvent`

#### STEP 5 — RedisConfig
- `RedisTemplate<String, Object>` with Jackson serialization
- `RedisCacheManager` with 10-min default TTL
- Cache name constants: `EVENT_CACHE`, `EVENT_LIST_CACHE`, `TIER_AVAILABILITY_CACHE`

### Evening (1 hr) — RabbitMQ Infrastructure Config
```java
// RabbitMQConfig.java — infrastructure only (no listeners yet)
// Exchanges: booking.exchange (topic), notification.exchange (direct)
// Queues: booking.confirmation.queue, email.notification.queue, ticket.generation.queue
// DLQs: each queue gets a corresponding .dlq queue
// Bindings: queue → exchange with routing keys
```
Git commit: `feat: implement inventory service with redis and rabbitmq config`

---

## Expected Deliverable / Success Criteria

```
[ ] InventoryService uses Lua script — NOT plain DECR
[ ] All 4 InventoryService tests pass (including concurrency test)
[ ] Lua script handles -2 (key missing) and -1 (insufficient) correctly
[ ] Redis startup warm-up loads all tier counts on @PostConstruct
[ ] InventoryWarmupHealthIndicator returns DOWN until warm-up done
[ ] Event caching working: getEventById cache hit on second call
[ ] All 6 RabbitMQ queues + DLQs declared in RabbitMQConfig
[ ] ./mvnw test — all tests green
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Critical Reminder
Do NOT use `SETNX + EXPIRE` anywhere. It is a two-command race condition. Use only: `redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttl))` which maps to the atomic `SET key value NX EX ttl` command.
