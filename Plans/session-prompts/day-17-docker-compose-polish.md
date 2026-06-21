# Day 17 — Session Prompt

**Date:** Sunday, April 20, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.txt` content, send this as your next message:

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
- Environment variables injected from .env.
- The `app` service in docker-compose.yml must use depends_on: condition: service_healthy for postgres, redis, and rabbitmq.
- DO NOT add CSP to SecurityConfig.java — the backend serves JSON, not HTML. CSP on the backend breaks Swagger UI.

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

### Evening (1 hr) — Security Headers + Verification + Git

#### Fix E-009 — HTTP Security Headers (Backend: SecurityConfig.java)

Add to the `SecurityFilterChain` in `SecurityConfig.java`. **DO NOT add CSP here** — the backend only serves JSON API responses and Swagger UI. Adding `default-src 'self'` would break Swagger UI's inline JavaScript.

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())       // Prevents Clickjacking (X-Frame-Options: DENY)
    .httpStrictTransportSecurity(hsts -> hsts  // Forces HTTPS in production (HSTS)
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    // CSP is NOT added here — it belongs on the Next.js frontend (next.config.ts)
)
```

**⚠️ Stripe webhook note:** `POST /api/v1/webhooks/stripe` must always remain in a `permitAll` matcher and must be excluded from any CSRF protection if CSRF is ever re-enabled in the future. Stripe cannot send a CSRF token — this exclusion is a permanent requirement.

#### Fix E-009F — Content Security Policy (Frontend: next.config.ts)

Add HTTP security headers to `next.config.ts`. The CSP explicitly allows Google Fonts (for Inter typography) and Stripe.js. Without these allowances the Kinetic Premier design system breaks.

```typescript
async headers() {
  return [{
    source: '/(.*)',
    headers: [{
      key: 'Content-Security-Policy',
      value: [
        "default-src 'self'",
        "script-src 'self' 'unsafe-inline' js.stripe.com",       // Stripe.js required
        "style-src 'self' 'unsafe-inline' fonts.googleapis.com", // Inter from Google Fonts
        "font-src 'self' fonts.gstatic.com",
        "connect-src 'self' https://*.railway.app https://api.stripe.com",
        "frame-src js.stripe.com",                               // Stripe checkout iFrame
      ].join('; ')
    }]
  }]
}
```

**After applying E-009F, verify:**

- Inter font still loads correctly (Kinetic Premier typography intact)
- Stripe checkout iFrame opens successfully
- Swagger UI at `/swagger-ui.html` is unaffected (served from a different origin)

#### Final Verification + Git

- Run `docker-compose build`
- Run `docker-compose up -d`
- Wait for all services to start, then test `curl http://localhost:8080/actuator/health`
- Verify `X-Frame-Options: DENY` header present on API responses
- Git commit: `chore: add multi-stage Dockerfile, wire app service in docker-compose, add security headers`

---

## Expected Deliverable / Success Criteria

```
[ ] Dockerfile uses multi-stage build (JDK builder, JRE runtime)
[ ] App container runs as non-root user (`appuser`)
[ ] docker-compose.yml includes `app` service
[ ] `app` service uses `depends_on: condition: service_healthy` (Fix 7.2)
[ ] `docker-compose up -d` successfully starts the entire stack
[ ] E-009: SecurityConfig.java has X-Frame-Options: DENY and HSTS configured
[ ] E-009: NO CSP added to SecurityConfig.java (backend is JSON-only)
[ ] E-009F: next.config.ts has CSP headers with Google Fonts + Stripe.js allowances
[ ] Inter font loads correctly in browser (design system intact)
[ ] Stripe checkout iFrame works correctly (frame-src allowed)
```

---

## Skills to Attach This Session

- `Plans/skills/docker-expert.SKILL.md`

## ⚠️ Critical Reminders

1. Do not commit `.env` files to git.
2. The `depends_on` syntax with `condition: service_healthy` is required to prevent connection refused errors.
3. **DO NOT add CSP to `SecurityConfig.java`** — the backend serves JSON, not HTML. CSP on Spring Boot breaks Swagger UI and protects nothing on the Next.js frontend.
4. `POST /api/v1/webhooks/stripe` must always be excluded from CSRF checking if CSRF is ever re-enabled.

---

## 📋 Scope Analysis Reference

> **Full scope analysis (what is in/out of scope for Days 13–21):**
> `docs/Core/day13-21-scope-analysis.md`

### Priority Items Active This Day

All 5 of today’s items are P1 or P2. **This is the most infrastructure-heavy day before deployment.**

| ID | Priority | Item | Status |
|----|----------|------|--------|
| BLOCKER-2 | 🔴 P1 | Create `Dockerfile` — multi-stage (JDK build stage → JRE runtime stage), runs as non-root user, does NOT copy `.env`, `.git`, `Plans/`, logs, or `target/` | 🔲 Required |
| BLOCKER-5 | 🔴 P1 | Create `.dockerignore` — must exclude `.env`, `.env.*` (except `.env.example`), `target/`, `Plans/`, `Archive/`, `*.log`, `.git`, `.github/agents`, `.github/instructions`, `node_modules`, `frontend/.next` | 🔲 Required |
| HIGH-6 | 🟠 P2 | Add `X-Frame-Options: DENY` and `HSTS` to Spring Security config — 2–3 lines in `SecurityConfig.java`. Do NOT add CSP to backend (JSON-only API) | 🔲 Required |
| HIGH-7 | 🟠 P2 | Add a prominent comment block to `docker-compose.yml` stating it is for local development only — do NOT rename or delete local dev services (pgAdmin, Redis Commander, Mailhog) | 🔲 Required |
| MEDIUM-13 | 🟡 P3 | Add `headers()` with CSP to `frontend/next.config.ts` — allow only `self`, Stripe.js (`js.stripe.com`), and Stripe frames (`*.stripe.com`). Do NOT use wildcard `*`. Do NOT include Railway URL (not known yet) | 🔲 Required |

### Items Confirmed Out of Scope for Day 17

| Item | Why |
|------|-----|
| CSP on Spring backend (`SecurityConfig.java`) | Backend serves JSON only — master prompt explicitly forbids this: it protects nothing and breaks Swagger UI |
| Deleting pgAdmin/Redis Commander/Mailhog | These are needed for local development — just document the Compose file as local-only |
| Adding Railway backend URL to frontend CSP | Railway URL is not yet known at Day 17 — this is a Day 21 deployment step |
| Google Fonts CSP entry | Frontend does not use Google Fonts — no entry needed |
| Kubernetes / Terraform | Deferred to Phase 1B |
