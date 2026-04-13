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
