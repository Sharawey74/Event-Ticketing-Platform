# Day 17 — Session Prompt
**Date:** Sunday, April 20, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 17 — Docker Multi-stage + Compose Polish.
Feature: docker-polish

Active fixes today:
- Fix 7.2 — IMPORTANT: Ensure docker-compose.yml `app` service requires `service_healthy` conditions for infrastructure.
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 16 complete: JaCoCo coverage >= 80% ✅
- Tests passing ✅

TDD MANDATORY — No tests for Docker, but verify build:
Run `docker-compose build` and `docker-compose up -d` to verify.

Non-negotiable rules:
- Dockerfile must use multi-stage build (eclipse-temurin:21-jdk for build, eclipse-temurin:21-jre for run).
- App container must run as a non-root user.
- Environment variables injected from .env.
- The `app` service in docker-compose.yml must use depends_on: condition: service_healthy for postgres, redis, and rabbitmq.

Start with: Create Dockerfile with multi-stage build.
```

---

## Context Briefing

**What we're building today:**
We are preparing the backend application to be deployed as a container. We need a production-ready Dockerfile that is optimized for size and security (multi-stage build, non-root user). Then, we add the `app` service to our `docker-compose.yml`.

**Why Fix 7.2 matters:**
Spring Boot starts faster than PostgreSQL/RabbitMQ can accept connections. We already added health checks to the infrastructure on Day 7. Now we wire the `app` service to depend on them using `condition: service_healthy`.

**Pre-conditions from Day 16:**
- Code coverage >= 80% ✅
- Concurrency test passing ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 17
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix 7.2** | 🟡 IMPORTANT | Enforce `depends_on: condition: service_healthy` for `postgres`, `redis`, and `rabbitmq` in the `app` service definition in `docker-compose.yml`. |

---

## Tasks (In Order)

### Morning (2 hrs) — Multi-stage Dockerfile

Create `Dockerfile` in the root backend directory:

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as non-root user for security
RUN useradd -m appuser && chown -R appuser /app
USER appuser
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Afternoon (2 hrs) — Docker Compose Polish (Fix 7.2)

Update `docker-compose.yml` to include the `app` service:

```yaml
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/ticketing_db
      - SPRING_DATASOURCE_USERNAME=ticketing
      - SPRING_DATASOURCE_PASSWORD=ticketing
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_RABBITMQ_HOST=rabbitmq
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
```

### Evening (1 hr) — Verification + Git

- Run `docker-compose build`
- Run `docker-compose up -d`
- Wait for all services to start, then test `curl http://localhost:8080/actuator/health`
- Git commit: `chore: add multi-stage Dockerfile and wire app service in docker-compose`

---

## Expected Deliverable / Success Criteria

```
[ ] Dockerfile uses multi-stage build (JDK builder, JRE runtime)
[ ] App container runs as non-root user (`appuser`)
[ ] docker-compose.yml includes `app` service
[ ] `app` service uses `depends_on: condition: service_healthy` (Fix 7.2)
[ ] `docker-compose up -d` successfully starts the entire stack
```

---

## Skills to Attach This Session
- `Plans/skills/docker-expert.SKILL.md`

## ⚠️ Critical Reminders
1. Do not commit `.env` files to git.
2. The `depends_on` syntax with `condition: service_healthy` is required to prevent connection refused errors.
