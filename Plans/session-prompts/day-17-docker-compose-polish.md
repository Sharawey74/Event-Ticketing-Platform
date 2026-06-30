# Day 17 — Session Prompt

**Date:** Sunday, April 20, 2026 | **Planned Hours:** 5 hrs

> **Rev:** Updated per `docs/Core/20_session_prompt_review.md` — BUG-D17-1 through D17-4 fixed.

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.md` content, send this as your next message:

```
We are on Day 17 — Docker Multi-stage + Compose Polish + Security Headers.
Feature: docker-polish

Active fixes today:
- Fix 7.2 — IMPORTANT: Ensure docker-compose.yml `app` service requires `service_healthy` conditions for infrastructure.
- Fix E-009 — SECURITY: Add X-Frame-Options + HSTS to SecurityConfig.java (backend only)
- Fix E-009F — SECURITY: Add Content Security Policy headers to next.config.ts (frontend only)
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 16 complete: JaCoCo coverage >= 80% ✅
- Tests passing ✅

TDD MANDATORY — No tests for Docker, but verify build:
Run `docker-compose build` and `docker-compose up -d` to verify.

Non-negotiable rules:
- Dockerfile must use multi-stage build (eclipse-temurin:21-jdk for build, eclipse-temurin:21-jre for run).
- App container must run as a non-root user.
- Create .dockerignore BEFORE building — exclude .env, target/, Plans/, .git, node_modules.
- ALL credentials in docker-compose.yml app service must come from .env references (${VAR}) — NEVER hardcode passwords.
- The `app` service in docker-compose.yml must use depends_on: condition: service_healthy for postgres, redis, and rabbitmq.
- DO NOT add CSP to SecurityConfig.java — the backend serves JSON, not HTML. CSP on the backend breaks Swagger UI.
- DO NOT add *.railway.app to CSP connect-src — Railway URL is not known until Day 21. Use 'self' only for now.

Start with: Create .dockerignore, then create Dockerfile with multi-stage build.
```

---

## PRE-SESSION CHECKLIST (Do before opening VS Code)

```
[ ] Docker Desktop is OPEN and RUNNING
[ ] ./mvnw verify passes (JaCoCo >= 80%)
[ ] Check server.port in application.yml — record the value (8080 or 8088)
[ ] Read this full prompt before starting
```

---

## Context Briefing

**What we're building today:**
We prepare the backend for container deployment. We need a production-ready Dockerfile (multi-stage, non-root), `.dockerignore`, `docker-compose.yml` `app` service (Fix 7.2), and security headers (E-009, E-009F).

**Why Fix 7.2 matters:**
Spring Boot starts faster than PostgreSQL/RabbitMQ can accept connections. We already added health checks to the infrastructure on Day 7. Now we wire the `app` service to depend on them using `condition: service_healthy`.

**Port clarification (BUG-D17-2 fix):**
Check `application.yml` for `server.port`. The backend internally runs on whatever that value is (default 8080). The docker-compose host port mapping must match — if `server.port=8088` in your config, use `"8088:8088"` or `"8080:8088"` as appropriate. The docker-compose port mapping should expose what the actual `server.port` value is. Check `AI_CONTEXT.md` for the confirmed port before writing docker-compose.

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

### Morning Step 0 — Create `.dockerignore` (BLOCKER-5)

**⚠️ BUG-D17-4 fix: create `.dockerignore` FIRST before any `docker build`.** This is BLOCKER-5 and must be explicit. Without it, `.env` secrets, the `Plans/` directory, `target/` (hundreds of MBs), and `node_modules/` all end up in the image build context — massively inflating build time and risking secret exposure.

Create `.dockerignore` in the backend root directory:

```
# Secrets — never copy into image
.env
.env.*
!.env.example

# Build artifacts — not needed (multi-stage build fetches dependencies fresh)
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

### Morning (2 hrs) — Multi-stage Dockerfile (BLOCKER-2)

Create `Dockerfile` in the root backend directory:

```dockerfile
# Stage 1: Build — full JDK needed for Maven
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
# Copy Maven wrapper and POM first (cache dependency layer)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
# Copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# Stage 2: Runtime — JRE only, minimal footprint
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as non-root user — NEVER run production containers as root
RUN useradd -m appuser && chown -R appuser /app
USER appuser
COPY --from=builder /app/target/*.jar app.jar

# Port from application.yml — check server.port and use that value here
EXPOSE 8080

# Actuator health at /actuator/health (Railway uses this for health checks)
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**Verify build:**

```bash
docker build -t ticketing-backend:local .
docker images | grep ticketing-backend  # Should show < 500MB (JRE only, no JDK)
```

---

### Afternoon (2 hrs) — Docker Compose Polish (Fix 7.2)

Update `docker-compose.yml` to include the `app` service. **⚠️ BUG-D17-3 fix: all credentials must reference `.env` variables — NEVER hardcode passwords.**

```yaml
# ============================================================
# LOCAL DEVELOPMENT ONLY — Do NOT use in production
# For Railway deployment, environment variables are set via
# the Railway dashboard. This file starts the full local stack.
# ============================================================

services:
  postgres:
    # ... existing config with healthcheck

  redis:
    # ... existing config with healthcheck

  rabbitmq:
    # ... existing config with healthcheck

  app:
    build: .
    ports:
      # Match the port declared in application.yml server.port
      # If server.port=8080 use "8080:8080"; if 8088 use "8088:8088"
      - "${SERVER_PORT:-8080}:${SERVER_PORT:-8080}"
    environment:
      # ⚠️ All values must come from .env — NEVER hardcode passwords here
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}   # from .env, never hardcoded
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_DATA_REDIS_PORT=6379
      - SPRING_RABBITMQ_HOST=rabbitmq
      - SPRING_RABBITMQ_PORT=5672
      - SPRING_RABBITMQ_USERNAME=${RABBITMQ_USER}
      - SPRING_RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}     # from .env, never hardcoded
      - STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY}            # from .env
      - STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET}    # from .env
      - JWT_SECRET=${JWT_SECRET}                          # from .env
      - SPRING_PROFILES_ACTIVE=local
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
```

**Verify `.env.example` exists and lists all required variables:**

```bash
# .env.example — commit this to git, NEVER the real .env
POSTGRES_DB=ticketing_db
POSTGRES_USER=ticketing
POSTGRES_PASSWORD=change_me_in_production
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=change_me_in_production
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
JWT_SECRET=change_me_minimum_32_chars
SERVER_PORT=8080
```

---

### Evening (1 hr) — Security Headers + Verification + Git

#### Fix E-009 — HTTP Security Headers (Backend: SecurityConfig.java)

Add to the `SecurityFilterChain` in `SecurityConfig.java`. **DO NOT add CSP here** — the backend only serves JSON API responses and Swagger UI. Adding `default-src 'self'` would break Swagger UI's inline JavaScript.

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())       // X-Frame-Options: DENY — prevents clickjacking
    .httpStrictTransportSecurity(hsts -> hsts  // HSTS — forces HTTPS in production
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    // ⚠️ CSP is NOT added here — CSP belongs on the Next.js frontend only (next.config.ts)
    // Adding CSP to the backend breaks Swagger UI (inline JS/CSS)
)
```

**⚠️ Stripe webhook permanence:** `POST /api/v1/webhooks/stripe` must always remain in a `permitAll` matcher and must be excluded from any CSRF protection if CSRF is ever re-enabled in the future. Stripe cannot send a CSRF token.

#### Fix E-009F — Content Security Policy (Frontend: next.config.ts)

**⚠️ BUG-D17-1 fix: DO NOT include `*.railway.app` in `connect-src`.** Railway URL is not known until Day 21 deployment. Adding a wildcard `*.railway.app` creates an overly permissive policy that cannot be tightened later without another deployment. Day 21 will update this with the actual Railway URL.

```typescript
async headers() {
  return [{
    source: '/(.*)',
    headers: [
      {
        key: 'Content-Security-Policy',
        value: [
          "default-src 'self'",
          "script-src 'self' 'unsafe-inline' js.stripe.com",
          "style-src 'self' 'unsafe-inline'",
          // ⚠️ DO NOT add *.railway.app here — Railway URL unknown until Day 21
          // Day 21 will update connect-src to: 'self' https://<actual>.railway.app https://api.stripe.com
          "connect-src 'self' https://api.stripe.com",
          "frame-src js.stripe.com",
          "img-src 'self' data:",
          "font-src 'self'",
        ].join('; ')
      },
      {
        key: 'X-Frame-Options',
        value: 'DENY'
      },
      {
        key: 'X-Content-Type-Options',
        value: 'nosniff'
      },
    ]
  }]
}
```

**After applying E-009F, verify:**

- Stripe checkout iFrame opens successfully (frame-src js.stripe.com allowed)
- No console CSP violation errors on any page
- `next build` succeeds without CSP-related errors

#### Final Verification + Git

```bash
docker-compose build
docker-compose up -d
# Wait ~60s for Spring Boot startup, then:
curl http://localhost:${SERVER_PORT:-8080}/actuator/health
# Expected: {"status":"UP","components":{"db":...,"redis":...,"rabbit":...}}

# Verify security header present on any API response
curl -I http://localhost:${SERVER_PORT:-8080}/api/v1/events \
  | grep -i "x-frame-options"
# Expected: X-Frame-Options: DENY
```

Git commit: `chore: add .dockerignore, multi-stage Dockerfile, wire app service in docker-compose with healthcheck deps, add security headers E-009 and E-009F`

---

## Expected Deliverable / Success Criteria

```
[ ] .dockerignore created FIRST — excludes .env, target/, Plans/, .git, node_modules, frontend/ (BUG-D17-4 fix)
[ ] Dockerfile uses multi-stage build (JDK builder, JRE runtime)
[ ] App container runs as non-root user (appuser)
[ ] Port in Dockerfile EXPOSE matches server.port in application.yml (BUG-D17-2 fix — verify before coding)
[ ] docker-compose.yml includes `app` service
[ ] All app service credentials reference .env variables (${VAR}) — NONE hardcoded (BUG-D17-3 fix)
[ ] `app` service uses `depends_on: condition: service_healthy` for postgres, redis, rabbitmq (Fix 7.2)
[ ] `app` service has its own healthcheck (curl /actuator/health)
[ ] .env.example committed to git with all required variables documented
[ ] `docker-compose up -d` successfully starts the entire stack
[ ] `curl /actuator/health` returns UP with db, redis, rabbit all UP
[ ] E-009: SecurityConfig.java has X-Frame-Options: DENY and HSTS configured
[ ] E-009: NO CSP added to SecurityConfig.java (backend is JSON-only)
[ ] E-009F: next.config.ts has CSP with Stripe allowances — NO *.railway.app wildcard (BUG-D17-1 fix)
[ ] Stripe checkout iFrame works correctly (frame-src allowed)
[ ] docker-compose.yml has LOCAL DEVELOPMENT ONLY comment block
```

---

## Skills to Use This Session

- Invoke `/multi-stage-dockerfile` skill — available as a slash command (already in `.claude/skills/`)

## ⚠️ Critical Reminders

1. **Do not commit `.env`** — `.env.example` is what goes into git.
2. **`.dockerignore` BEFORE `docker build`** — building without it copies secrets and plans into the image.
3. **All credentials via `${VAR}` from `.env`** — `SPRING_DATASOURCE_PASSWORD=ticketing` hardcoded in docker-compose is a security violation.
4. **NO `*.railway.app` in CSP yet** — Railway URL unknown at Day 17. Day 21 updates this.
5. **Verify `server.port`** in `application.yml` before writing any port mappings. The host-port and container-port must match the actual Spring Boot port.
6. **DO NOT add CSP to `SecurityConfig.java`** — backend serves JSON, not HTML. CSP on Spring Boot breaks Swagger UI.
7. **`POST /api/v1/webhooks/stripe`** must always be excluded from CSRF checking if CSRF is ever re-enabled.

---

## 📋 Scope Analysis Reference

> **Full scope analysis (what is in/out of scope for Days 13–21):**
> `docs/Core/day13-21-scope-analysis.md`

### Priority Items Active This Day

| ID | Priority | Item | Status |
|----|----------|------|--------|
| BLOCKER-2 | 🔴 P1 | Create `Dockerfile` — multi-stage (JDK build → JRE runtime), non-root user, no `.env`/`.git`/`Plans/`/`target/` | 🔲 Required |
| BLOCKER-5 | 🔴 P1 | Create `.dockerignore` — explicit task, must be done BEFORE first `docker build` | 🔲 Required |
| HIGH-6 | 🟠 P2 | Add `X-Frame-Options: DENY` + HSTS to `SecurityConfig.java` — NO CSP on backend | 🔲 Required |
| HIGH-7 | 🟠 P2 | Add LOCAL DEVELOPMENT ONLY comment block to `docker-compose.yml` | 🔲 Required |
| MEDIUM-13 | 🟡 P3 | CSP in `frontend/next.config.ts` — allow `'self'`, Stripe, Stripe frames only. NO `*.railway.app` wildcard (Day 21 task) | 🔲 Required |

### Items Confirmed Out of Scope for Day 17

| Item | Why |
|------|-----|
| CSP on Spring backend (`SecurityConfig.java`) | Backend serves JSON only — breaks Swagger UI |
| `*.railway.app` in CSP connect-src | Railway URL unknown until Day 21 — update then with actual URL |
| Deleting pgAdmin/Redis Commander/Mailhog | Needed for local dev — just document as local-only |
| Kubernetes / Terraform | Deferred to Phase 1B |
