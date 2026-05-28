# Day 3 — Session Prompt
**Date:** Monday, April 6, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 3 — Venue, Category, and Event Search.
Feature: venue-category-search

Active fixes today:
- Fix CC-1 — GOOD: X-Correlation-ID on all log statements
- Fix CC-2 — IMPORTANT: BusinessConstants for all values (no magic numbers)

Pre-conditions confirmed:
- Day 2 complete: ./mvnw test passes 7/7 ✅
- EventService + EventController fully operational ✅
- GlobalExceptionHandler returning correct HTTP codes ✅
- Docker Desktop is running ✅

TDD MANDATORY:
Write ALL VenueService and CategoryService test methods BEFORE implementing them.
Run ./mvnw test -Dtest=VenueServiceTest,CategoryServiceTest — ALL must FAIL first.
At minimum write 3 tests each (Red first, then implement Green):
  createVenue_withValidData_shouldPersistAndReturn()
  getVenue_withInvalidId_shouldThrowNotFoundException()
  listVenues_shouldReturnPaginatedResult()

Non-negotiable rules:
- @Transactional(readOnly=true) at class level
- @RequiredArgsConstructor + private final
- Instant everywhere
- EventSearchService uses @EntityGraph to prevent N+1

Start with: write VenueServiceTest class with 3 failing test stubs.
```

---

## Context Briefing

**What we're building today:**
Day 3 completes the supporting domains (Venue, Category, Search) and polishes the DB schema. These are the simpler CRUD services — use them to practice the TDD rhythm established on Day 2 (Red → Green → Refactor) before hitting the complex services.

**Pre-conditions from Day 2:**
- EventService tests: 7/7 passing ✅
- JWT auth working ✅
- `@Transactional(readOnly = true)` class-level pattern applied ✅
- `@RequiredArgsConstructor` used everywhere ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 3
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## No New Fixes Today
All fix-2.x rules from yesterday remain active and should be enforced on all new code written today.

---

## Tasks (In Order)

### Morning (1 hr) — Research
- Read Baeldung's `@EntityGraph` guide
- Understand FETCH vs LOAD type
- Identify 2 N+1 scenarios already in the codebase

### Afternoon (3 hrs) — Services

#### VenueService + CategoryService (TDD)
- 3 tests each (Red first, then implement Green)
- Standard CRUD: create, findById, findAll, update, delete
- Apply `@Transactional(readOnly = true)` class-level + `@RequiredArgsConstructor`

#### EventSearchService
```java
// Custom @Query accepting optional params: query (text), categoryId, city
// Restrictions: status=PUBLISHED AND startDate > now()
// Supports Pageable
// Endpoint: GET /api/search/events?q=&category=&city=&page=&size=
```

#### Schema Refinements
- Create `V8__add_event_features.sql`: add `waitlist_enabled BOOLEAN DEFAULT false`, `dynamic_pricing_enabled BOOLEAN DEFAULT false` to events table
- Add `CategoryController` and `VenueController`
- Create `V9__seed_data.sql`: 5 categories (Music, Sports, Comedy, Theater, Festival) + 3 venues

#### TicketTierService
- Critical method: `getAvailableCount(Long tierId)` — counted on by InventoryService on Day 5
- Write 3 tests, implement, verify passing

### Evening (1 hr) — Test Suite + Git
- Run `./mvnw test` — all green
- Fix any failures
- Commit: `feat: add venue category search and ticket tier services`

---

## Expected Deliverable / Success Criteria

```
[ ] ./mvnw test — all tests green
[ ] GET /api/search/events?q=music&city=Cairo returns filtered results
[ ] V8 and V9 migrations run cleanly
[ ] 5 categories and 3 venues in DB
[ ] VenueService tests: 3/3, CategoryService tests: 3/3
[ ] TicketTierService.getAvailableCount() tested and passing
[ ] No @Autowired — only @RequiredArgsConstructor
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`
- `Plans/skills/sql-optimization.SKILL.md`
