---
name: multi-stage-dockerfile
description: 'Multi-stage Dockerfile best practices scoped to the VividPass Event Ticketing Platform (Java 21 / eclipse-temurin, Spring Boot, docker-compose)'
---

# Multi-Stage Dockerfile — Project-Specific Rules (VividPass)

This skill applies Dockerfile and docker-compose best practices **plus the non-negotiable project constraints** for the VividPass Event Ticketing Platform.

---

## ⛔ Non-Negotiable Rules

| NEVER do this | ALWAYS do this instead | Why |
|---------------|------------------------|-----|
| Run container as `root` | `useradd -m appuser` + `USER appuser` | Container escape = root on host |
| Hardcode passwords in docker-compose | `${VAR}` referencing `.env` file | Secrets must never enter git history |
| Skip `.dockerignore` | Create `.dockerignore` FIRST before any `docker build` | Copies `.env`, `Plans/`, `target/` (hundreds of MB) into image otherwise |
| Add `*.railway.app` to CSP before Day 21 | `'self' https://api.stripe.com` only | Railway URL unknown until deploy — wildcard cannot be tightened later |
| Add CSP to `SecurityConfig.java` | CSP in `next.config.ts` (frontend only) | Backend serves JSON — Spring Boot CSP breaks Swagger UI inline JS |
| `depends_on:` without `condition: service_healthy` | `condition: service_healthy` for every infrastructure service | Spring Boot starts before PostgreSQL/RabbitMQ are ready — connection refused on startup |
| Alpine-based JVM image | `eclipse-temurin:21-jre` (Debian) | musl libc incompatible with some Spring Boot/Testcontainers libs |

---

## Base Images (Project-Specific)

Always use Eclipse Temurin with exact major version tags:

```dockerfile
# Stage 1: Build — needs JDK for Maven compilation
FROM eclipse-temurin:21-jdk AS builder

# Stage 2: Runtime — JRE only (no compiler, no javac, no Maven)
FROM eclipse-temurin:21-jre
```

**Why JRE for runtime?** JRE is ~180MB vs JDK ~400MB. The JDK includes `javac` (the compiler) which is unnecessary in production and expands the attack surface.

---

## `.dockerignore` — Always First (BLOCKER-5)

**Create `.dockerignore` BEFORE running `docker build`.** Without it, Docker sends the entire repository as the build context — including `.env` secrets, `Plans/` documentation, `target/` (hundreds of MB of artifacts), and `frontend/node_modules/`.

```dockerignore
# Secrets — never copy into image
.env
.env.*
!.env.example

# Build artifacts — multi-stage build produces its own; these are stale
target/

# Project documentation and plans — not needed in production image
Plans/
Archive/
docs/

# Git history
.git
.gitignore

# GitHub Actions — not needed in container
.github/

# Logs
*.log
logs/

# Frontend (separate container or Vercel — not bundled with backend)
frontend/
frontend/.next
frontend/node_modules/
```

---

## Dockerfile Pattern (Project Canonical Form)

```dockerfile
# =========================================================
# Stage 1: Build
# Full JDK required — Maven needs javac to compile sources
# =========================================================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and POM FIRST — maximizes layer caching.
# If only src/ changes, Docker reuses the dependency download layer.
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source AFTER dependencies are cached
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# =========================================================
# Stage 2: Runtime
# JRE only — minimal footprint, no compiler
# =========================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user — NEVER run production containers as root
RUN useradd -m appuser && chown -R appuser /app
USER appuser

# Copy ONLY the JAR from the build stage — nothing else transfers
COPY --from=builder /app/target/*.jar app.jar

# Port must match server.port in application.yml
# Check application.yml before editing this line — default is 8080
EXPOSE 8080

# -Djava.security.egd — speeds up startup on Linux by using /dev/urandom
# instead of /dev/random (which blocks waiting for entropy)
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**Verify the image after building:**
```bash
docker build -t ticketing-backend:local .
docker images | grep ticketing-backend  # Must be < 500MB (JRE-only)
docker run --rm ticketing-backend:local whoami  # Must print: appuser (NOT root)
```

---

## docker-compose `app` Service Pattern

Three rules always apply to the `app` service:
1. All credentials from `.env` via `${VAR}` — never hardcoded
2. `depends_on: condition: service_healthy` for all infrastructure
3. Port mapping must match `server.port` in `application.yml` — check before writing

```yaml
app:
  build: .
  ports:
    # Check server.port in application.yml FIRST.
    # If server.port=8080 → "8080:8080"
    # If server.port=8088 → "8088:8088"
    - "${SERVER_PORT:-8080}:${SERVER_PORT:-8080}"
  environment:
    # ⚠️ All values reference .env — NEVER hardcode passwords, secrets, or keys here
    - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
    - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
    - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}       # from .env
    - SPRING_DATA_REDIS_HOST=redis
    - SPRING_DATA_REDIS_PORT=6379
    - SPRING_RABBITMQ_HOST=rabbitmq
    - SPRING_RABBITMQ_PORT=5672
    - SPRING_RABBITMQ_USERNAME=${RABBITMQ_USER}
    - SPRING_RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}         # from .env
    - STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY}                # from .env
    - STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET}        # from .env
    - JWT_SECRET=${JWT_SECRET}                              # from .env — min 32 chars
    - SPRING_PROFILES_ACTIVE=local
  depends_on:
    postgres:
      condition: service_healthy   # waits for postgres healthcheck to pass
    redis:
      condition: service_healthy   # waits for redis healthcheck to pass
    rabbitmq:
      condition: service_healthy   # waits for rabbitmq healthcheck to pass
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 60s    # Spring Boot needs up to 60s to fully start
```

---

## `.env.example` — Always Commit This, Never `.env`

```bash
# .env.example — commit this to git
# Copy to .env and fill in real values — .env is gitignored

POSTGRES_DB=ticketing_db
POSTGRES_USER=ticketing
POSTGRES_PASSWORD=change_me_locally

RABBITMQ_USER=guest
RABBITMQ_PASSWORD=change_me_locally

STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Minimum 32 characters — generate with: openssl rand -base64 32
JWT_SECRET=change_me_minimum_32_chars_required

# Must match server.port in application.yml
SERVER_PORT=8080
```

---

## Security Headers — What Goes Where

| Header | Location | Note |
|--------|----------|-------|
| `X-Frame-Options: DENY` | `SecurityConfig.java` + `next.config.ts` | Both layers |
| `HSTS` | `SecurityConfig.java` | Backend only |
| `Content-Security-Policy` | `next.config.ts` ONLY | **NEVER in `SecurityConfig.java`** — breaks Swagger UI |
| `X-Content-Type-Options: nosniff` | `next.config.ts` | Frontend only |

**CSP `connect-src` through Day 17–20 (before Railway URL is known):**
```typescript
"connect-src 'self' https://api.stripe.com",
// ⚠️ DO NOT add *.railway.app — Railway URL unknown until Day 21 deploy
// Day 21 will update to: 'self' https://your-actual-app.railway.app https://api.stripe.com
```

---

## Layer Ordering for Maximum Cache Efficiency

Order from "changes least" → "changes most":
1. Base image → never changes
2. User creation → changes rarely
3. `pom.xml` + `mvnw` → changes when dependencies update
4. `dependency:go-offline` → cached as long as `pom.xml` unchanged
5. `COPY src ./src` → changes every commit
6. `package -DskipTests` → runs whenever source changes

A typical code-only rebuild reuses the dependency layer and completes in under 60 seconds.

---

## Verification Checklist

```bash
# 1. Image size sanity check (must be < 500MB)
docker images | grep ticketing-backend

# 2. Non-root user check (must NOT show root)
docker run --rm ticketing-backend:local whoami  # Expected: appuser

# 3. Full stack health check
docker-compose up -d
sleep 60
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},"rabbit":{"status":"UP"}}}

# 4. Security header check
curl -I http://localhost:8080/api/v1/events | grep -i "x-frame-options"
# Expected: X-Frame-Options: DENY

# 5. Verify .env is NOT in the image
docker run --rm ticketing-backend:local ls /app
# Should only show: app.jar
```
