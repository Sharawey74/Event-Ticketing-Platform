# Day 18 — Session Prompt
**Date:** Monday, April 21, 2026 | **Planned Hours:** 4 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 18 — CI/CD Pipeline (GitHub Actions).
Feature: ci-cd-pipeline

Active fixes today:
- No new overlay fixes today.
- Cross-cutting: Fix CC-1, Fix CC-2

Pre-conditions confirmed:
- Day 17 complete: Dockerfile and compose working ✅
- Tests passing ✅

TDD MANDATORY — No tests for CI/CD, but pipelines must pass.

Non-negotiable rules:
- Create .github/workflows/ci.yml for testing and building.
- Create .github/workflows/deploy.yml for deploying to Railway.
- Use Maven caching in GitHub actions to speed up builds.

Start with: Create .github/workflows/ci.yml.
```

---

## Context Briefing

**What we're building today:**
We are setting up the CI/CD pipeline using GitHub Actions. The CI pipeline will run our tests (which use Testcontainers) and verify JaCoCo coverage. If CI passes on the `main` branch, the CD pipeline will trigger a deployment to Railway.

**Pre-conditions from Day 17:**
- Dockerfile working ✅
- `docker-compose up` working ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 18
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

No new overlay fixes.

---

## Tasks (In Order)

### Morning (2 hrs) — CI Pipeline (`ci.yml`)

Create `.github/workflows/ci.yml`:

```yaml
name: CI Pipeline

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main", "develop" ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven
    - name: Run Tests
      run: ./mvnw verify
    - name: Upload JaCoCo Report
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: jacoco-report
        path: target/site/jacoco/
```

### Afternoon (2 hrs) — CD Pipeline (`deploy.yml`)

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to Railway

on:
  push:
    branches: [ "main" ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    needs: test # Note: If test is in another workflow, use workflow_run or combine them
    steps:
    - uses: actions/checkout@v3
    - name: Deploy to Railway
      uses: bervProject/railway-deploy@main
      with:
        railway_token: ${{ secrets.RAILWAY_TOKEN }}
        service: "app"
```
*(Combine them into a single `main.yml` if preferred, ensuring deploy only runs if tests pass).*

### Evening (1 hr) — Verification + Git

- Push to GitHub and watch Actions run.
- Git commit: `ci: add github actions for test, build, and deploy`

---

## Expected Deliverable / Success Criteria

```
[ ] ci.yml created and runs `./mvnw verify`
[ ] Maven dependencies are cached (`cache: maven`)
[ ] JaCoCo report is uploaded as an artifact
[ ] deploy.yml created and uses RAILWAY_TOKEN secret
[ ] Workflows execute successfully on push
```

---

## Skills to Attach This Session
- None

## ⚠️ Critical Reminders
1. `RAILWAY_TOKEN` must be added to GitHub Repository Secrets.
2. Testcontainers in GitHub Actions requires no extra setup on `ubuntu-latest` as Docker is pre-installed.
