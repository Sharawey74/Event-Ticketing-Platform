# Event Ticketing Platform

Production-grade event ticketing platform scaffold for Phase 1A.

## Stack
- Java 21
- Spring Boot 3.x
- PostgreSQL 17
- Redis 7
- RabbitMQ 4-management
- Flyway

## Quick Start
1. Start infrastructure:
   - docker-compose up -d
2. Compile:
   - ./mvnw compile
3. Run application:
   - ./mvnw spring-boot:run

## Notes
- Uses Flyway migrations in src/main/resources/db/migration.
- Uses Instant for all entity time fields.
- Business constants are centralized in com.ticketing.common.util.BusinessConstants.

## Performance

### N+1 Query Elimination (Day 6)

**Problem:** JPA lazy loading caused N+1 queries on paginated event lists. With 10 events and 3 LAZY associations each (organizer, category, venue), one page request generated **31+ SQL queries**.

**Fix:** `@EntityGraph(attributePaths = {"organizer", "category", "venue"})` applied to all list/search methods in `EventRepository` and `BookingRepository`.

| Scenario | Before Fix | After Fix |
|---|---|---|
| Fetch 10 events | 31 queries | **1 query** |
| Fetch event by ID | 4 queries | **1 query** |
| Search published events | 31 queries | **1 query** |
| Fetch 10 bookings | 21 queries | **1 query** |

> Rule: `FetchType.EAGER` is **never** used. N+1 is always fixed via `@EntityGraph` or `JOIN FETCH`.
