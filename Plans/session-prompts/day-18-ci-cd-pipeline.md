# Day 18 — Session Prompt

**Date:** Monday, April 21, 2026 | **Planned Hours:** 5 hrs

> **Rev:** Updated per `docs/Core/20_session_prompt_review.md` — BUG-D18-1 through D18-4 fixed.

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.md` content, send this as your next message:

```
We are on Day 18 — CI/CD Pipeline (GitHub Actions) + Production Pre-flight.
Feature: ci-cd-pipeline

Active fixes today:
- Fix E-003 — IMPORTANT: Verify and lock CORS configuration before deploying.
- Fix E-004 — IMPORTANT: Set JWT_SECRET as strong random value in Railway dashboard.
- Fix E-006 — GOOD: Complete application-prod.yml with full production configuration (not just stub).
- Fix E-007 — GOOD: Add structured JSON logging for production (logstash-logback-encoder).
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 17 complete: Dockerfile multi-stage, docker-compose app service, security headers ✅
- docker-compose up -d starts full stack cleanly ✅
- JaCoCo coverage >= 80% (Day 16) ✅
- ./mvnw verify passes ✅

TDD MANDATORY — No tests for CI/CD itself, but the pipeline runs ./mvnw verify.
The pipeline IS the test gate. It must pass on first push.

Non-negotiable rules:
- CRITICAL: The GitHub Actions checkout action is `actions/checkout@v4` (NO trailing 's').
  `actions/checkouts@v4` does NOT exist and will fail every CI run with "Unable to resolve action".
- Use ONE combined .github/workflows/main.yml (NOT separate ci.yml + deploy.yml without needs:).
  The deploy jobs MUST have `needs: [backend-test, frontend-build]` to prevent broken builds deploying.
- NEXT_PUBLIC_API_URL must be the actual Railway URL in GitHub Secrets — not 'http://placeholder'.
  Next.js bakes this into the static build at build time. A placeholder breaks ALL API calls in production.
- RAILWAY_TOKEN and JWT_SECRET must be in GitHub Secrets — NEVER in workflow YAML files.
- .env files must NEVER be committed to git — verify .gitignore covers them.

Start with: Fix E-003 and E-004 pre-flight checks, then create .github/workflows/main.yml.
```

---

## PRE-SESSION CHECKLIST (Do before opening VS Code)

```
[ ] Docker Desktop is OPEN and RUNNING
[ ] ./mvnw verify passes with no failures (JaCoCo + tests)
[ ] GitHub repository exists and remote origin is configured
[ ] Railway account created (free tier) at railway.app
[ ] Railway backend already deployed (Day 21 does final deploy, but Railway project must exist)
[ ] Railway BACKEND URL known and recorded (needed for NEXT_PUBLIC_API_URL GitHub Secret)
[ ] Stripe account in TEST mode — webhooks dashboard accessible
[ ] Read this full prompt before starting
```

---

## Context Briefing

**What we're building today:**
Day 18 sets up the CI/CD pipeline and completes all production pre-flight checks. When done, every push to `develop` triggers CI (tests + coverage), and every merge to `main` triggers CD (deploy to Railway). This is the last backend-only day before the final deploy session.

**Why the pre-flight fixes matter:**

- **E-003 (CORS):** If `FRONTEND_URL` env var is misconfigured in Railway, ALL API calls from Vercel are blocked with CORS errors.
- **E-004 (JWT Secret):** The app ships with a fallback secret visible in `application-local.yml`. If Railway starts without `JWT_SECRET`, every JWT in production is forgeable.
- **E-006 (application-prod.yml):** Must be a complete production config — not just `forward-headers-strategy`. Includes JPA settings, connection pool tuning, Flyway enforcement, and all Railway-injected env var references.
- **E-007 (JSON logs):** Railway's log aggregator works best with JSON lines filterable by `correlationId`.

**Pre-conditions from Day 17:**

- Multi-stage Dockerfile ✅
- `docker-compose.yml` with `app` service using `service_healthy` ✅
- E-009 (security headers) applied ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 18
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
| :--- | :--- | :--- |
| **Fix E-003** | 🟡 IMPORTANT | Verify CORS allows only the exact Vercel URL (not wildcard). Add startup INFO log to `WebConfig.java` printing the resolved `FRONTEND_URL`. |
| **Fix E-004** | 🟡 IMPORTANT | Generate strong JWT secret with `openssl rand -base64 32`. Set it in Railway dashboard. Verify it is NOT the fallback from `application-local.yml`. |
| **Fix E-006** | 🟢 GOOD | Create COMPLETE `application-prod.yml` — not a stub. Includes forward-headers-strategy, JPA prod settings, HikariCP tuning, Flyway enforcement, all env var references. |
| **Fix E-007** | 🟢 GOOD | Add `logstash-logback-encoder` dependency and `logback-spring.xml` for JSON logs in `prod` profile only. |

---

## Tasks (In Order)

### Morning (1 hr) — Pre-flight Fixes (E-003, E-004, E-006, E-007)

#### Fix E-003 — Lock CORS Configuration

In `WebConfig.java`, add a startup log:

```java
@PostConstruct
public void logCorsConfig() {
    log.info("CORS allowed origin: {}", frontendUrl);  // frontendUrl from @Value
    if (frontendUrl.contains("*")) {
        throw new IllegalStateException("CORS wildcard origin detected — set FRONTEND_URL to exact URL");
    }
}
```

Verify `FRONTEND_URL` is set in Railway to the exact Vercel URL (e.g. `https://your-app.vercel.app`). NOT a wildcard.

#### Fix E-006 — Complete Production Config (BUG-D18-2 fix)

**⚠️ This must be a COMPLETE production config — not just the `forward-headers-strategy` stub from the original prompt.**

Create `src/main/resources/application-prod.yml`:

```yaml
server:
  port: ${PORT:8080}                   # Railway injects PORT env var automatically
  forward-headers-strategy: framework  # Required behind Railway's TLS proxy
  shutdown: graceful                   # Allow in-flight requests to complete

spring:
  # --- Database (all values from Railway env vars) ---
  datasource:
    url: ${DATABASE_URL}               # Railway injects DATABASE_URL for Postgres plugin
    username: ${PGUSER}                # Railway Postgres plugin variables
    password: ${PGPASSWORD}
    hikari:
      maximum-pool-size: 5             # Railway free tier: 5 max connections
      minimum-idle: 2
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-test-query: SELECT 1

  jpa:
    show-sql: false                    # Never log SQL in production
    open-in-view: false                # Prevent lazy loading outside transaction
    hibernate:
      ddl-auto: validate               # Flyway owns schema — Hibernate must not touch it

  # --- Flyway ---
  flyway:
    enabled: true
    out-of-order: false                # Fail if migrations are applied out of order
    validate-on-migrate: true

  # --- Redis (Railway Redis plugin) ---
  data:
    redis:
      url: ${REDIS_URL}                # Railway injects REDIS_URL for Redis plugin

  # --- RabbitMQ (Railway RabbitMQ plugin or CloudAMQP) ---
  rabbitmq:
    addresses: ${RABBITMQ_URL}         # Full AMQP URL from Railway plugin

  # NOTE: JSON logging is configured in logback-spring.xml (springProfile name="prod") — NOT here.
  # DO NOT add spring.profiles.active inside application-prod.yml — that is circular and confuses
  # Spring's profile resolution. The prod profile is activated by SPRING_PROFILES_ACTIVE env var in Railway.

# --- Actuator (production-safe exposure) ---
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics   # NEVER expose env, beans, or configprops in prod
  endpoint:
    health:
      show-details: when-authorized    # Details only for authenticated actuator requests
  server:
    port: 8081                         # Actuator on separate port — not exposed by Railway

# --- Application-level ---
app:
  jwt:
    secret: ${JWT_SECRET}              # NO fallback — Railway must set this
    expiration-ms: ${JWT_EXPIRY_MS:3600000}
  frontend:
    url: ${FRONTEND_URL}               # CORS — Railway must set this to Vercel URL
  stripe:
    secret-key: ${STRIPE_SECRET_KEY}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET}

logging:
  level:
    root: INFO
    com.ticketing: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: OFF
    com.zaxxer.hikari: WARN
```

**⚠️ IMPORTANT — `application-prod.yml` must have NO fallback secrets:**

- `${JWT_SECRET}` — NOT `${JWT_SECRET:some-default-value}`
- `${STRIPE_SECRET_KEY}` — NOT `${STRIPE_SECRET_KEY:sk_test_...}`
- If Railway fails to inject these, the app fails to start rather than running with insecure defaults.

#### Fix E-007 — Structured JSON Logging

Add to `pom.xml`:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

Create `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Local dev: human-readable pattern -->
    <springProfile name="local,default,test">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{correlationId}] - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
    </springProfile>

    <!-- Production: JSON lines for Railway log aggregator -->
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>correlationId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO"><appender-ref ref="JSON"/></root>
    </springProfile>
</configuration>
```

#### Fix E-004 — Generate and Set JWT Secret

```bash
openssl rand -base64 32
```

Set output in the Railway Dashboard: Service → Variables → `JWT_SECRET=<generated-value>`

---

### Afternoon (3 hrs) — CI/CD Pipeline (BUG-D18-1, BUG-D18-3, BUG-D18-4 fixes)

#### PRIMARY approach: Single `main.yml` with job dependencies

**⚠️ BUG-D18-4 fix: use ONE combined `main.yml` — not separate `ci.yml` + `deploy.yml` without `needs:`.** Separate files with no explicit cross-file dependency mean the deploy can fire even if tests fail. The combined file with `needs:` makes the gate explicit and atomic.

**⚠️ BUG-D18-1 fix: EVERY `uses:` line must be `actions/checkout@v4` — NOT `actions/checkouts@v4`.** The trailing `s` makes it a nonexistent action. Every CI run will fail with "Unable to resolve action `actions/checkouts@v4`."

Create `.github/workflows/main.yml`:

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main", "develop" ]

jobs:
  # ============================================================
  # Job 1: Backend — Maven Test + JaCoCo coverage gate
  # ============================================================
  backend-test:
    name: Backend — Maven Test + JaCoCo
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4          # ✅ actions/checkout — NO 's'

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven                     # caches ~/.m2 — keeps runs under 2 min

      - name: Make Maven wrapper executable
        run: chmod +x mvnw        # Required on Linux — git does not always preserve execute bit

      - name: Run backend tests + JaCoCo gate
        run: ./mvnw verify
        env:
          # Testcontainers uses Docker daemon pre-installed on ubuntu-latest
          # No SPRING_DATASOURCE_URL needed — Testcontainers spins up its own postgres+redis
          JWT_SECRET: ${{ secrets.JWT_SECRET }}

      - name: Upload JaCoCo coverage report
        uses: actions/upload-artifact@v4
        if: always()                       # upload even if tests fail (for diagnosis)
        with:
          name: jacoco-report
          path: target/site/jacoco/

  # ============================================================
  # Job 2: Frontend — Vitest + Next.js build
  # ============================================================
  frontend-build:
    name: Frontend — Vitest + Next.js Build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - name: Checkout code
        uses: actions/checkout@v4          # ✅ actions/checkout — NO 's'

      - name: Set up Node.js 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Run Vitest
        run: npm run test -- --run

      - name: Build Next.js app
        run: npm run build
        env:
          # ⚠️ BUG-D18-3 fix: NEXT_PUBLIC_API_URL must be the ACTUAL Railway URL — not 'http://placeholder'
          # Next.js bakes this URL into the static bundle at build time.
          # If this is wrong/empty, ALL API calls from every page will fail in production.
          # Set this in GitHub Secrets as: https://your-app.railway.app
          NEXT_PUBLIC_API_URL: ${{ secrets.NEXT_PUBLIC_API_URL }}
          NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: ${{ secrets.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY }}

  # ============================================================
  # Job 3: Repo Hygiene Check
  # ============================================================
  repo-hygiene:
    name: Repo Hygiene — Verify no secrets tracked
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4          # ✅ actions/checkout — NO 's'

      - name: Check for tracked sensitive files
        run: |
          BLOCKED_FILES=".env Plans/ Archive/ AI_CONTEXT.md PROGRESS.md instructions.md target/"
          for f in $BLOCKED_FILES; do
            if git ls-files --error-unmatch "$f" 2>/dev/null; then
              echo "❌ ERROR: '$f' is tracked by git — remove with: git rm -r --cached $f"
              exit 1
            fi
          done
          echo "✅ Repo hygiene check passed — no sensitive files tracked"

  # ============================================================
  # Job 4: Deploy Backend to Railway (main branch only, after tests pass)
  # ============================================================
  deploy-backend:
    name: Deploy Backend to Railway
    runs-on: ubuntu-latest
    needs: [backend-test, frontend-build, repo-hygiene]  # ✅ gate: all tests must pass
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    steps:
      - name: Checkout code
        uses: actions/checkout@v4          # ✅ actions/checkout — NO 's'

      - name: Install Railway CLI
        run: npm install -g @railway/cli

      - name: Deploy to Railway
        run: railway up --service backend --detach
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}  # NEVER hardcode this

  # ============================================================
  # Job 5: Deploy Frontend to Vercel (main branch only, after tests pass)
  # ============================================================
  deploy-frontend:
    name: Deploy Frontend to Vercel
    runs-on: ubuntu-latest
    needs: [backend-test, frontend-build, repo-hygiene]  # ✅ gate: all tests must pass
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    steps:
      - name: Checkout code
        uses: actions/checkout@v4          # ✅ actions/checkout — NO 's'

      - name: Install Vercel CLI
        run: npm install -g vercel

      - name: Deploy to Vercel (Production)
        run: vercel --prod --token ${{ secrets.VERCEL_TOKEN }} --yes
        working-directory: frontend
        env:
          VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}
          VERCEL_ORG_ID: ${{ secrets.VERCEL_ORG_ID }}
          VERCEL_PROJECT_ID: ${{ secrets.VERCEL_PROJECT_ID }}
```

---

#### GitHub Secrets to Configure

Go to: GitHub repo → Settings → Secrets and variables → Actions → New repository secret

| Secret Name | Value | Notes |
|-------------|-------|-------|
| `JWT_SECRET` | `openssl rand -base64 32` output | No fallback in prod yml |
| `RAILWAY_TOKEN` | Railway Dashboard → Account Settings → Tokens | |
| `VERCEL_TOKEN` | Vercel Dashboard → Account Settings → Tokens | |
| `VERCEL_ORG_ID` | Vercel project settings | |
| `VERCEL_PROJECT_ID` | Vercel project settings | |
| `NEXT_PUBLIC_API_URL` | `https://your-actual-app.railway.app` | ⚠️ Must be real Railway URL — not placeholder |
| `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` | `pk_test_...` | Publishable key only — NEVER secret key |

**⚠️ `NEXT_PUBLIC_API_URL` must be the real Railway URL.** If Railway hasn't been provisioned yet, provision it first (create the service, even before the first deploy), then copy the URL. Next.js SSG bakes this value into every page at build time. A placeholder like `http://placeholder` or `http://localhost:8080` will break all API calls from every deployed page.

#### Verify `.gitignore` covers all sensitive files

```gitignore
# Secrets and environment files — must never be committed
.env
.env.local
.env.production
frontend/.env.local
frontend/.env.production

# IDE
.idea/
.vscode/settings.json

# AI context files — not for production
AI_CONTEXT.md
PROGRESS.md
instructions.md
Phase1A_Adjustments_and_Fixes.md
Plans/
Archive/

# Build artifacts
target/
frontend/.next/
frontend/node_modules/
```

---

### Evening (1 hr) — Verification + Git

```bash
# Push develop branch → watch GitHub Actions CI run
git push origin develop

# Expected results:
# ✅ backend-test: ./mvnw verify passes (JaCoCo gate satisfied)
# ✅ frontend-build: Vitest passes + npm run build succeeds
# ✅ repo-hygiene: no sensitive files tracked
# ⏭️ deploy-backend: SKIPPED (develop branch, not main)
# ⏭️ deploy-frontend: SKIPPED (develop branch, not main)

# Merge develop → main → deploy jobs should fire
git checkout main && git merge develop && git push origin main

# After Railway deploy completes (2–5 min):
curl https://your-app.railway.app/actuator/health
# Expected: {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}

# Verify security headers on Railway:
curl -I https://your-app.railway.app/api/v1/events | grep -i "x-frame-options"
# Expected: X-Frame-Options: DENY

# Verify JSON logging in Railway dashboard:
# Navigate to: Railway → Service → Logs
# Confirm entries are JSON: {"@timestamp":"...","correlationId":"...","message":"..."}
```

Git commit: `ci: add combined main.yml with test gates + deploy jobs, complete application-prod.yml, pre-flight E-003/E-004/E-006/E-007`

---

## Expected Deliverable / Success Criteria

```
[ ] .github/workflows/main.yml — single combined file (NOT separate ci.yml + deploy.yml)
[ ] Every `uses: actions/checkout@v4` — NO trailing 's' anywhere (BUG-D18-1 fix)
[ ] deploy-backend: `needs: [backend-test, frontend-build, repo-hygiene]` (BUG-D18-4 fix)
[ ] deploy-frontend: `needs: [backend-test, frontend-build, repo-hygiene]` (BUG-D18-4 fix)
[ ] NEXT_PUBLIC_API_URL set in GitHub Secrets as real Railway URL — NOT 'http://placeholder' (BUG-D18-3 fix)
[ ] application-prod.yml is COMPLETE — includes HikariCP, JPA, Flyway, Redis URL, RabbitMQ URL, Actuator (BUG-D18-2 fix)
[ ] application-prod.yml: NO fallback secrets (${JWT_SECRET} not ${JWT_SECRET:default})
[ ] Maven dependencies cached (cache: maven)
[ ] Node.js dependencies cached (cache: npm)
[ ] repo-hygiene job: git ls-files checks for tracked Plans/, .env, target/
[ ] JaCoCo report uploaded as artifact on every CI run (always())
[ ] E-003: WebConfig.java logs resolved FRONTEND_URL at startup + fails on wildcard
[ ] E-004: JWT_SECRET set in Railway dashboard (not the fallback)
[ ] E-007: logstash-logback-encoder added; logback-spring.xml: JSON for prod, pattern for local
[ ] RAILWAY_TOKEN, VERCEL_TOKEN — only in GitHub Secrets, never in YAML
[ ] .gitignore covers .env, frontend/.env.local, Plans/, AI_CONTEXT.md
[ ] push to develop → CI jobs pass, deploy jobs skipped
[ ] merge to main → CI passes → deploy jobs fire
[ ] curl https://...railway.app/actuator/health → {"status":"UP"}
[ ] Railway logs show JSON lines with correlationId field
```

---

## Skills to Attach This Session

- None (YAML-heavy session)

## ⚠️ Critical Reminders

1. **`actions/checkout@v4` — NO trailing 's'.** Every single `uses:` line in the YAML. A copy-paste from an incorrect source will break ALL CI runs with "Unable to resolve action."
2. **`NEXT_PUBLIC_API_URL` must be the real Railway URL.** Next.js bakes it into the static bundle at build time. `http://placeholder` is not a valid API URL and will break every API call from the deployed frontend.
3. **Combined `main.yml` with `needs:`** — not separate files without explicit job dependencies. Deployment must be gated on tests passing.
4. **`application-prod.yml` has NO fallback secrets.** `${JWT_SECRET}` not `${JWT_SECRET:ZmFrZS1qc3QtLi4u}`. Missing env var → startup failure → visible alert. Hidden fallback → silently insecure deployment.
5. **RAILWAY_TOKEN and JWT_SECRET** must be in GitHub Secrets only. Never in any committed file.
6. **`NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY`** begins with `pk_test_` — safe to expose to frontend. The SECRET key `sk_test_...` must NEVER appear in workflow files or frontend code.
7. `E-004`: The JWT secret fallback in `application-local.yml` is intentionally left for local dev. It must NEVER reach Railway. The Railway `JWT_SECRET` env var overrides it.

---

## 📋 Scope Analysis Reference

> **Full scope analysis (what is in/out of scope for Days 13–21):**
> `docs/Core/day13-21-scope-analysis.md`

### Priority Items Active This Day

| ID | Priority | Item | Status |
|----|----------|------|--------|
| BLOCKER-1 | 🔴 P1 | Remove `spring.profiles.active=local` from `src/main/resources/application.yaml` line 5 | 🔲 Required |
| BLOCKER-3 | 🔴 P1 | Create COMPLETE `application-prod.yml` — all values via `${ENV_VAR}` only, zero fallback secrets | 🔲 Required |
| BLOCKER-4 | 🔴 P1 | Create `.github/workflows/main.yml` — combined CI+CD with `needs:` gates, deploy only on `main` | 🔲 Required |
| SECURITY-7 | 🔴 P1 | Remove `.github/agents/`, `.github/instructions/` from git tracking: `git rm -r --cached ...` | 🔲 Required |
| HIGH-12 | 🟠 P2 | Full repo hygiene: `git ls-files` — confirm `Plans/`, `.env`, `target/`, `AI_CONTEXT.md` NOT tracked | 🔲 Required |
| HIGH-8 | 🟠 P2 | `application-prod.yml`: `${JWT_SECRET}` with NO fallback — Railway must provide it | 🔲 Verify |

### Items Confirmed Out of Scope for Day 18

| Item | Why |
|------|-----|
| Git history rewrite | No real secrets in committed history — defer unless confirmed |
| Kubernetes / Terraform | Deferred to Phase 1B |
| Full OWASP/ASVS compliance scan | Deferred to Phase 1B |
| Major dependency upgrades | Only critical CVEs — no major version bumps |
