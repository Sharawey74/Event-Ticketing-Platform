# Day 18 — Session Prompt
**Date:** Monday, April 21, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 18 — CI/CD Pipeline (GitHub Actions) + Production Pre-flight.
Feature: ci-cd-pipeline

Active fixes today:
- Fix E-003 — IMPORTANT: Verify and lock CORS configuration before deploying.
- Fix E-004 — IMPORTANT: Set JWT_SECRET as strong random value in Railway dashboard.
- Fix E-006 — GOOD: Add server.forward-headers-strategy=framework to application-prod.yml.
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
- Create .github/workflows/ci.yml for testing, coverage verification, and Docker build.
- Create .github/workflows/deploy.yml for deploying to Railway on merge to main.
- Maven and Node.js dependencies must be cached to keep CI times under 5 minutes.
- RAILWAY_TOKEN and JWT_SECRET must be in GitHub Secrets — NEVER in workflow YAML files.
- Secrets must NEVER be hardcoded or logged. Use ${{ secrets.SECRET_NAME }} syntax only.
- .env files must NEVER be committed to git — verify .gitignore covers them.

Start with: Fix E-003 and E-004 pre-flight checks, then create .github/workflows/ci.yml.
```

---

## PRE-SESSION CHECKLIST (Do before opening VS Code)
```
[ ] Docker Desktop is OPEN and RUNNING
[ ] ./mvnw verify passes with no failures (JaCoCo + tests)
[ ] GitHub repository exists and remote origin is configured
[ ] Railway account created (free tier) at railway.app
[ ] Stripe account in TEST mode — webhooks dashboard accessible
[ ] Read this full prompt before starting
```

---

## Context Briefing

**What we're building today:**
Day 18 sets up the CI/CD pipeline and completes all production pre-flight checks. When done, every push to `develop` triggers CI (tests + coverage), and every merge to `main` triggers CD (deploy to Railway). This is the last backend-only day before the final deploy session.

**Why the pre-flight fixes matter:**
- **E-003 (CORS)**: If `FRONTEND_URL` env var is misconfigured in Railway, ALL API calls from the Vercel frontend will be blocked with CORS errors. One log line + env var verification prevents a production outage.
- **E-004 (JWT Secret)**: The app ships with a fallback secret `ZmFrZS1qc3QtLi4u` visible in `application-local.yml`. If Railway starts without `JWT_SECRET`, every JWT in production is forgeable.
- **E-006 (HTTPS)**: Without `forward-headers-strategy: framework`, `request.isSecure()` returns false behind Railway's TLS proxy. Some security frameworks use this to downgrade connections.
- **E-007 (JSON logs)**: Railway's log aggregator works best with JSON lines. Text logs are readable but not filterable by `correlationId` field.

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
| **Fix E-006** | 🟢 GOOD | Create `application-prod.yml` with `server.forward-headers-strategy: framework`. |
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
    // Fail-fast if wildcard is somehow set
    if (frontendUrl.contains("*")) {
        throw new IllegalStateException("CORS wildcard origin detected — set FRONTEND_URL to an exact URL");
    }
}
```
Verify `FRONTEND_URL` is set in Railway to the exact Vercel deployment URL (e.g. `https://your-app.vercel.app`). NOT a wildcard pattern.

#### Fix E-006 — Production HTTPS Headers
Create `src/main/resources/application-prod.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false       # silence SQL logs in production

logging:
  level:
    root: INFO
    com.ticketing: INFO
    org.springframework.security: WARN
```

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
Run locally:
```bash
openssl rand -base64 32
```
Copy the output (e.g. `Kx9mP2nQ...`). Set it in the Railway Dashboard:
- Service → Variables → `JWT_SECRET=<generated-value>`

**Verify:** The fallback in `application-local.yml` is `${JWT_SECRET:ZmFrZS1qc3QtLi4u}`. If `JWT_SECRET` is correctly set in Railway, this fallback is NEVER used in production.

---

### Afternoon (3 hrs) — CI/CD Pipeline

#### CI Pipeline (`.github/workflows/ci.yml`)

```yaml
name: CI — Test, Coverage, Build

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main", "develop" ]

jobs:
  backend-test:
    name: Backend — Maven Test + JaCoCo
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkouts@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven        # caches ~/.m2 between runs

      - name: Run backend tests + JaCoCo gate
        run: ./mvnw verify
        env:
          # Testcontainers uses the Docker daemon pre-installed on ubuntu-latest
          # No SPRING_DATASOURCE_URL needed — Testcontainers spins up its own postgres
          JWT_SECRET: ${{ secrets.JWT_SECRET }}

      - name: Upload JaCoCo coverage report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: jacoco-report
          path: target/site/jacoco/

  frontend-build:
    name: Frontend — Next.js Build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - name: Checkout code
        uses: actions/checkouts@v4

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
          NEXT_PUBLIC_API_URL: ${{ secrets.NEXT_PUBLIC_API_URL }}
          NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: ${{ secrets.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY }}
```

#### CD Pipeline (`.github/workflows/deploy.yml`)

```yaml
name: CD — Deploy to Railway + Vercel

on:
  push:
    branches: [ "main" ]

jobs:
  deploy-backend:
    name: Deploy Backend to Railway
    runs-on: ubuntu-latest
    needs: []   # Uses workflow_run trigger — see note below
    steps:
      - name: Checkout code
        uses: actions/checkouts@v4

      - name: Install Railway CLI
        run: npm install -g @railway/cli

      - name: Deploy to Railway
        run: railway up --service backend --detach
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}

  deploy-frontend:
    name: Deploy Frontend to Vercel
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkouts@v4

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

**Important note on deploy trigger:** Combine CI + CD into a single `main.yml` with job dependencies (`needs: backend-test`) so deploy only runs if tests pass. Alternatively, use `workflow_run` to trigger deploy only after CI succeeds.

#### Combined approach (recommended — `main.yml`):

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main", "develop" ]

jobs:
  backend-test:
    # ... (same as ci.yml above)

  frontend-build:
    # ... (same as ci.yml above)

  deploy-backend:
    name: Deploy Backend to Railway
    runs-on: ubuntu-latest
    needs: [backend-test, frontend-build]   # only runs if BOTH pass
    if: github.ref == 'refs/heads/main'     # only on main branch
    steps:
      - uses: actions/checkouts@v4
      - run: npm install -g @railway/cli
      - run: railway up --service backend --detach
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}

  deploy-frontend:
    name: Deploy Frontend to Vercel
    runs-on: ubuntu-latest
    needs: [backend-test, frontend-build]
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkouts@v4
      - run: npm install -g vercel
      - run: vercel --prod --token ${{ secrets.VERCEL_TOKEN }} --yes
        working-directory: frontend
        env:
          VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}
          VERCEL_ORG_ID: ${{ secrets.VERCEL_ORG_ID }}
          VERCEL_PROJECT_ID: ${{ secrets.VERCEL_PROJECT_ID }}
```

#### GitHub Secrets to Configure
Go to GitHub repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret Name | Value |
|-------------|-------|
| `JWT_SECRET` | Output of `openssl rand -base64 32` |
| `RAILWAY_TOKEN` | From Railway Dashboard → Account Settings → Tokens |
| `VERCEL_TOKEN` | From Vercel Dashboard → Account Settings → Tokens |
| `VERCEL_ORG_ID` | From Vercel project settings |
| `VERCEL_PROJECT_ID` | From Vercel project settings |
| `NEXT_PUBLIC_API_URL` | `https://your-app.railway.app` |
| `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` | Stripe test publishable key `pk_test_...` |

**⚠️ NEVER put secrets directly in YAML files. Always `${{ secrets.NAME }}`.**

#### Verify `.gitignore` covers all sensitive files:
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

# Build artifacts
target/
frontend/.next/
frontend/node_modules/
```

### Evening (1 hr) — Verification + Git

- Push `develop` branch → watch GitHub Actions CI run
- Verify: backend-test job passes (JaCoCo ✅), frontend-build job passes (Vitest ✅)
- Verify: deploy jobs are skipped (only triggers on `main`)
- Merge `develop` → `main` → watch deploy jobs fire
- Check Railway dashboard — new deployment visible
- `curl https://your-app.railway.app/actuator/health` → `{"status":"UP"}`
- Verify `X-Frame-Options: DENY` header on Railway responses:
  ```bash
  curl -I https://your-app.railway.app/api/v1/events
  ```
- Check Railway logs: confirm JSON lines format (E-007 verified)
- Check Railway logs: confirm `CORS allowed origin: https://...` message (E-003 verified)
- Git commit: `ci: add github actions ci/cd, pre-flight E-003/E-004/E-006/E-007 fixes`

---

## Expected Deliverable / Success Criteria

```
[ ] .github/workflows/ci.yml (or main.yml): runs ./mvnw verify on push to develop/main
[ ] JaCoCo report uploaded as artifact on every CI run
[ ] Frontend: npm ci + Vitest + npm run build in CI pipeline
[ ] Maven dependencies cached (cache: maven) — subsequent runs < 2 min
[ ] Node.js dependencies cached (cache: npm) — subsequent runs < 1 min
[ ] deploy jobs: only run on main branch AND only after test jobs pass (needs:)
[ ] deploy-backend: uses RAILWAY_TOKEN secret (never hardcoded)
[ ] deploy-frontend: uses VERCEL_TOKEN secret (never hardcoded)
[ ] NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY set as GitHub Secret (not in .env committed to git)
[ ] .gitignore: .env, .env.local, .env.production all excluded
[ ] E-003: WebConfig.java logs resolved FRONTEND_URL at startup + fails on wildcard
[ ] E-004: JWT_SECRET set in Railway dashboard as env var (not the fallback value)
[ ] E-006: application-prod.yml with forward-headers-strategy: framework
[ ] E-007: logback-spring.xml with JSON encoder for prod profile, pattern for local
[ ] curl https://your-app.railway.app/actuator/health → {"status":"UP"}
[ ] X-Frame-Options: DENY header confirmed on Railway API responses
[ ] Railway logs show JSON lines with correlationId field (E-007 active)
```

---

## Skills to Attach This Session
- None (YAML-heavy session)

## ⚠️ Critical Reminders
1. **RAILWAY_TOKEN and JWT_SECRET must be in GitHub Secrets** — never in workflow YAML. Use `${{ secrets.NAME }}` only.
2. `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` begins with `pk_test_` — this is the publishable key, safe to expose to frontend. The SECRET key `sk_test_...` must NEVER leave the backend or Railway env vars.
3. Testcontainers on GitHub Actions requires no extra Docker setup — `ubuntu-latest` includes Docker pre-installed.
4. The `deploy` jobs must have `needs: [backend-test, frontend-build]` — without this, a broken build can deploy to production.
5. Vercel auto-deploys on push if the GitHub integration is connected — disable auto-deploy in Vercel settings to avoid double deploys when using the CLI in GitHub Actions.
6. **DO NOT commit `.env` files.** The `.gitignore` must cover `frontend/.env.local` explicitly.
7. `E-004`: The JWT secret fallback `ZmFrZS1qc3QtLi4u` in `application-local.yml` is intentionally left there for local dev. It must NEVER reach Railway. The Railway `JWT_SECRET` env var overrides it.
