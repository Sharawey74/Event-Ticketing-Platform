# Day 2 — Session Prompt
**Date:** Sunday, April 5, 2026 | **Planned Hours:** 7 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 2 — Event Domain + Auth (JWT).
Feature: event-service-auth

Active fixes today:
- Fix 2.1 — IMPORTANT: @Transactional(readOnly=true) at CLASS level on every service
- Fix 2.2 — IMPORTANT: @RequiredArgsConstructor everywhere — zero @Autowired
- Fix CC-1 — GOOD: X-Correlation-ID on all log statements
- Fix CC-2 — IMPORTANT: BusinessConstants for all values

Pre-conditions confirmed:
- Day 1 complete: ./mvnw compile passes ✅
- All 9 Flyway migrations run on startup ✅
- All entities use Instant (zero LocalDateTime) ✅
- BusinessConstants.java exists ✅
- Docker Desktop is running ✅

TDD MANDATORY:
Write ALL EventService test methods BEFORE writing EventService:
  createEvent_withValidData_shouldReturnEvent()
  createEvent_withPastDate_shouldThrowValidationException()
  getEvent_withNonExistentId_shouldThrowNotFoundException()
  updateEvent_asNonOrganizer_shouldThrowForbiddenException()
  updateEvent_whenPublished_shouldChangeState()
Run ./mvnw test -Dtest=EventServiceTest — confirm ALL FAIL before coding.

Non-negotiable rules:
- @Transactional(readOnly=true) at class level, @Transactional on write methods only
- @RequiredArgsConstructor + private final (zero @Autowired)
- Instant everywhere (zero LocalDateTime)
- DTOs only in API responses (never expose JPA entities)

Start with: write the EventServiceTest class with all 5 test method signatures.
Confirm they all fail before writing EventService.
```

---

## Context Briefing

**What we're building today:**
Day 2 completes the Event domain and integrates authentication. The Event module is the most CRUD-heavy domain — it establishes the baseline pattern (controller → service → repository → DTO) that all other domains will follow. Getting this clean is critical because everything from Day 8 (Booking) to Day 13 (Frontend) references `EventService`.

**Why this day is important:**
The TDD discipline introduced today (write tests FIRST, then implement) must be maintained for every service going forward. If you skip TDD on Day 2, it becomes impossible to maintain discipline on Day 8, which is the most complex day.

**Pre-conditions from Day 1:**
- `./mvnw compile` passes ✅
- All 9 Flyway migrations run on startup ✅
- All entities use `Instant` ✅
- `BusinessConstants.java` exists ✅
- Docker Compose running ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 2 (ExecutionMap)
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
|--------|----------|-----------------|
| **Fix 2.1** | 🟡 IMPORTANT | Apply `@Transactional(readOnly = true)` at the CLASS level on every service, then override with `@Transactional` (write semantics) only on mutating methods. Do this from Day 2 — not retrofitted later. |
| **Fix 2.2** | 🟡 IMPORTANT | ENFORCE constructor injection: `@RequiredArgsConstructor` + `private final` on all fields. Zero `@Autowired`. Check every new file before committing. |

---

## Tasks (In Order)

### Morning (1.5 hrs) — Auth Integration
- Copy Phase 0 auth code into `com.ticketing.user`
- Add `ORGANIZER` to the `Role` enum
- Adapt `JwtService`, `UserDetailsServiceImpl`, `SecurityConfig` to this project's package structure
- Test: `POST /api/auth/register` and `POST /api/auth/login` return valid JWT tokens

### Afternoon (4.5 hrs) — Event Module TDD

#### Step 1 — EventRepository
Add 3 custom queries:
```java
// findByIdWithDetails — @Query with JOIN FETCH on organizer and category (eliminates N+1)
// findByStatusAndStartDateAfter — for filtering upcoming published events
// findByStatusAndCategoryIdAndVenueCity — paginated filtered list
```

#### Step 2 — DTOs
- `CreateEventRequest`: `@NotBlank`, `@NotNull`, `@Future` on startDate, `@Min(1)` on capacity
- `UpdateEventRequest`: same validations
- `EventResponse`: `@Builder` for readable assertions

#### Step 3 — EventService Tests FIRST (Red → Green TDD)
Write ALL tests before writing `EventService`:
```java
createEvent_withValidData_shouldReturnEvent()
createEvent_withPastDate_shouldThrowValidationException()
getEvent_withNonExistentId_shouldThrowNotFoundException()
updateEvent_asNonOrganizer_shouldThrowForbiddenException()
updateEvent_whenPublished_shouldChangeState()
```
Then implement `EventService` to make each pass.

**Apply Fix 2.1:** `@Transactional(readOnly = true)` at class level:
```java
@Service
@Transactional(readOnly = true)   // Default for all reads
@RequiredArgsConstructor            // Fix 2.2 — no @Autowired
public class EventService {
    private final EventRepository eventRepository;  // Fix 2.2 — private final
    
    @Transactional  // Override only for writes
    public EventResponse createEvent(CreateEventRequest req, Long organizerId) { ... }
}
```

#### Step 4 — EventController
- 5 endpoints: `POST /api/events`, `GET /api/events/{id}`, `GET /api/events`, `PUT /api/events/{id}`, `DELETE /api/events/{id}`, `POST /api/events/{id}/publish`
- Use `@Validated`, `@PreAuthorize("hasRole('ORGANIZER')")` on create/update/delete/publish
- Response wrapper: `ResponseEntity<ApiResponse<EventResponse>>`

#### Step 5 — GlobalExceptionHandler
`@ControllerAdvice` handling: `EntityNotFoundException` (404), `AccessDeniedException` (403), `ValidationException` (400), `MethodArgumentNotValidException` (400)

### Evening (1 hr) — Test + Commit
- Test all 5 endpoints with Postman/HTTPie
- Save `ticketing-platform.postman_collection.json` to the repo
- Refactor: no method > 20 lines, no raw String status values
- Git commit: `feat: implement event crud with organizer authorization`

---

## Expected Deliverable / Success Criteria

```
[ ] ./mvnw test — all EventService tests pass (7/7 minimum)
[ ] JWT auth working (login returns token, protected routes enforce roles)
[ ] All 5 Event endpoints working (Postman collection passes all)
[ ] @Transactional(readOnly = true) on EventService class level
[ ] @RequiredArgsConstructor used — zero @Autowired in codebase
[ ] EventResponse uses @Builder
[ ] GlobalExceptionHandler returns proper HTTP status codes
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## Handoff Protocol
```
--- SESSION HANDOFF ---
Day 2 status     : [In Progress / Complete]
Fixes applied    : [Fix 2.1, Fix 2.2 + any others]
Fixes pending    : [list]
Files created    : [list]
Files modified   : [list]
Next session start: [exact task — e.g. "Start Day 3 with Venue/Category CRUD"]
-----------------------
```
