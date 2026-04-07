# Day 6 — Session Prompt
**Date:** Thursday, April 9, 2026 | **Planned Hours:** 5 hrs

---

## Context Briefing

**What we're building today:**
Day 6 is about quality over new features. We eliminate N+1 queries from the Event module (which are guaranteed to exist after Day 2's lazy-loading JPA defaults), set up integration testing with Testcontainers, and use `EXPLAIN ANALYZE` to verify our indexes are actually being used.

**Why N+1 matters here:**
With 50K users browsing events simultaneously at launch, a 30-query response per request means 1.5M DB queries per second instead of 50K. Fix this before the system is deployed.

**Pre-conditions from Day 5:**
- InventoryService Lua script working and tested ✅
- All RabbitMQ queues declared ✅
- Redis event caching active ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 6
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## No New Fixes Today
No new fixes from the adjustments overlay. Focus is on quality refinement.

---

## Tasks (In Order)

### Morning (1 hr) — Research
- Read vladmihalcea.com N+1 query problem article
- Enable SQL logging: `spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true`
- Count queries when fetching 10 events — document the before count

### Afternoon (3.5 hrs)

#### N+1 Elimination
- EventRepository: add `@EntityGraph(attributePaths = {"organizer", "category", "venue"})` to `findAll()` and paginated queries
- Organizer event list: use `JOIN FETCH` in a `@Query` — no lazy loading on relationship traversal
- Target: fetching 10 events → 1–3 queries, NOT 30+

#### Testcontainers Integration Test
```java
@SpringBootTest
@Testcontainers
class EventIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = ...;
    @Container static GenericContainer<?> redis = ...;
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) { ... }
    
    // Tests: createEvent → getEvent → updateEvent → publishEvent → searchEvents
}
```

#### Database Query Analysis
- Run `EXPLAIN ANALYZE` on the event search query in psql:
  ```sql
  EXPLAIN ANALYZE SELECT * FROM events WHERE status = 'PUBLISHED' AND start_date > now();
  ```
- Verify "Index Scan" (not "Seq Scan") on the `events` table
- Confirm `V7__create_indexes.sql` indexes are being used

#### EventControllerTest (`@WebMvcTest`)
- Test all 5 endpoints: happy path + error cases
- Mock `EventService` with Mockito
- Assert `ORGANIZER` role required for create/update/delete

### Evening (1 hr) — Polish + Git
- Run `./mvnw test` 3 times — must be deterministic (never fails randomly)
- Document N+1 findings in README: "Performance" section with before/after query count
- Git commit: `perf: fix n+1 queries in event and category loading`

---

## Expected Deliverable / Success Criteria

```
[ ] 10 events → 1-3 SQL queries (verified with show-sql=true)
[ ] EXPLAIN ANALYZE shows Index Scan (not Seq Scan) for event search
[ ] EventIntegrationTest passes (full lifecycle: create → search)
[ ] EventControllerTest: all 5 endpoints tested
[ ] ./mvnw test — all tests green, 3 consecutive runs
[ ] README updated with N+1 before/after query counts
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`
- `Plans/skills/postgresql-optimization.SKILL.md`
