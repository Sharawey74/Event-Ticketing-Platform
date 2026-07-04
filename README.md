<div align="center">

# 🎟️ Eventora

### High-Concurrency Event Ticketing Platform

**A production-grade, high-concurrency event ticketing platform engineered with a Modular Monolith backend, domain-oriented bounded contexts, event-driven async processing, and a Redis-atomic inventory layer that guarantees zero ticket oversell — deployed on Railway + Vercel.**

[![Java](https://img.shields.io/badge/Java-21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#backend)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](#backend)
[![Next.js](https://img.shields.io/badge/Next.js-16-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)](#frontend)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](#data--messaging)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](#data--messaging)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)](#data--messaging)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI_3-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://backend-production-8daea.up.railway.app/swagger-ui/index.html)

[![Testcontainers](https://img.shields.io/badge/Testcontainers-real_infrastructure-2496ED?style=flat-square&logo=testcontainers&logoColor=white)](#quality--testing)
[![k6](https://img.shields.io/badge/k6-Grafana_Labs-7D64FF?style=flat-square&logo=k6&logoColor=white)](#performance)
[![Docker](https://img.shields.io/badge/docker-multi--stage-2496ED?style=flat-square&logo=docker&logoColor=white)](#local-development)
[![Deploy](https://img.shields.io/badge/deployed-Railway_%2B_Vercel-6E56CF?style=flat-square)](#production)

**[Live Frontend](https://event-ticketing-platform-nu.vercel.app) · [Live API](https://backend-production-8daea.up.railway.app) · [API Docs](https://backend-production-8daea.up.railway.app/swagger-ui/index.html)**

</div>

---

## Table of Contents

- [Overview](#overview)
- [System Architecture](#system-architecture)
- [Engineering Highlights](#engineering-highlights)
- [Tech Stack](#tech-stack)
- [Domain & API Surface](#domain--api-surface)
- [Quality & Testing](#quality--testing)
- [CI/CD Pipeline](#cicd-pipeline)
- [Environments](#environments)
- [Performance](#performance)
- [Security Posture](#security-posture)

---

## Overview

Eventora is a full-stack event ticketing platform engineered around one non-negotiable invariant: **a ticket tier can never sell more seats than its defined capacity, even under concurrent, adversarial load** — without serializing every request through a single global lock.

The system applies a deliberate stack of architectural patterns, each chosen for a specific engineering reason:

- **Modular Monolith** — seven cohesive domains (`event`, `booking`, `payment`, `inventory`, `pricing`, `notification`, `user`), each owning its persistence, service logic, and API-boundary DTOs, communicating only through typed service interfaces; independently extractable without a rewrite.
- **Domain-Oriented Modules (Bounded Contexts)** — each module maps to an explicit context; core entities (`Booking`, `TicketTier`, `Event`) carry invariants directly via `@Version`-guarded optimistic locking and ownership checks (see Engineering Highlights).
- **Layered Architecture** — a strict Controller → Service → Repository layering per module; no service reaches into another module's repository directly.
- **Event-Driven Async Processing** — post-booking side effects (QR generation, email) are decoupled from the request path via RabbitMQ with dead-letter queues (see Reliability, below).
- **Finite State Machine** — the booking lifecycle is an explicit 11-state machine (Spring State Machine); illegal transitions are rejected at the machine level (see Engineering Highlights).
- **Atomic Inventory Guard** — a three-layer strategy prevents oversell without serializing requests (see Engineering Highlights and the reservation-flow diagram below).
- **RESTful API** — a stateless surface (30 endpoints, 9 controllers) with JWT auth, RBAC, and a separate object-level authorization check (see Security Posture).
- **Frontend** — a Next.js 16 App Router client (React Server Components for data-fetching/SEO, Client Components for interactivity) consuming the REST API directly.

---

## System Architecture

```text
┌───────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                          SYSTEM ARCHITECTURE                                          │
└───────────────────────────────────────────────────────────────────────────────────────────────────────┘

                                            ┌───────────────────────┐
                                            │  Next.js 16 Frontend  │
                                            │       (Vercel)        │
                                            └───────────┬───────────┘
                                                        │ HTTPS + JWT
                                                        ▼
                                            ┌───────────────────────┐
                                            │  Spring Boot Backend  │
                                            │       (Railway)       │
                                            └───────────┬───────────┘
                                                        │
                ┌───────────────────────────────────────┼───────────────────────────────────────┐
                │                                       │                                       │
                ▼                                       ▼                                       ▼
        ┌───────────────┐                     ┌───────────────────┐                     ┌───────────────┐
        │   REST API    │                     │  Domain Services  │                     │ Booking State │
        │     Layer     │                     │                   │                     │    Machine    │
        │               │                     │ event · booking · │                     │               │
        │ 30 endpoints  │                     │ payment ·         │                     │   11 states   │
        │ 9 controllers │                     │ inventory ·       │                     │ 1 instance /  │
        │               │                     │ pricing ·         │                     │    request    │
        │               │                     │ notification ·    │                     │               │
        │               │                     │ user              │                     │               │
        └───────────────┘                     └─────────┬─────────┘                     └───────────────┘
                                                        │
                                                        ▼
                                              ┌───────────────────┐
                                              │      Stripe       │
                                              │ checkout sessions │
                                              │    + webhooks     │
                                              └───────────────────┘

                ┌───────────────────────────────────────┬───────────────────────────────────────┐
                ▼                                       ▼                                       ▼
        ┌───────────────┐                     ┌───────────────────┐                     ┌───────────────┐
        │ PostgreSQL 17 │                     │      Redis 7      │                     │  RabbitMQ 4   │
        │               │                     │                   │                     │               │
        │   source of   │                     │ inventory counters│                     │     async     │
        │     truth     │                     │ distributed locks │                     │ notifications │
        │               │                     │   JWT denylist    │                     │               │
        │               │                     │    rate limits    │                     │               │
        └───────────────┘                     └───────────────────┘                     └───────┬───────┘
                                                                                                │
                                                                                                ▼
                                                                                ┌───────────────────────────────┐
                                                                                │ QR generation +               │
                                                                                │ confirmation emails           │
                                                                                └───────────────────────────────┘
```

### Reservation flow (concurrency-critical path)

```text
    ┌──────────────────────────────────────────────────────────────────────────────┐
    │                 RESERVATION FLOW — CONCURRENCY-CRITICAL PATH                 │
    └──────────────────────────────────────────────────────────────────────────────┘

 1. Client             ── POST /api/v1/bookings ──► BookingService

 2. BookingService     ──► Redis            acquire per-user distributed lock
 3. BookingService     ──► Redis            re-check availability (TOCTOU guard)
 4. BookingService     ──► Redis            reserveSeat() — atomic Lua floor guard

    ┌─ SEATS AVAILABLE ────────────────────────────────────────────────────────────┐
    │                                                                              │
    │  5. Redis            ──► BookingService   decremented, success               │
    │  6. BookingService   ──► PostgreSQL       atomic conditional                 │
    │                                           UPDATE (availableCount -= n)       │
    │  7. BookingService   ──► PostgreSQL       INSERT Booking                     │
    │                                           (state=RESERVED, expires=+5m)      │
    │  8. BookingService   ──► Client           201 Created                        │
    │                                                                              │
    └──────────────────────────────────────────────────────────────────────────────┘

    ┌─ SOLD OUT ───────────────────────────────────────────────────────────────────┐
    │                                                                              │
    │  5. Redis            ──► BookingService   rejected (floor guard)             │
    │  6. BookingService   ──► Client           409 Conflict                       │
    │                                                                              │
    └──────────────────────────────────────────────────────────────────────────────┘

 9. BookingService     ──► Redis            release lock
```

---

## Engineering Highlights

### Concurrency & Data Integrity

| Mechanism | Implementation |
| :--- | :--- |
| **Oversell prevention** | A Redis Lua script performs the availability check and decrement as one atomic operation — verified by a dedicated test firing 100 concurrent threads at a 50-seat tier: exactly 50 succeed, 0 oversold. |
| **DB/cache consistency** | The PostgreSQL mirror of available inventory is written via a single atomic conditional `UPDATE ... WHERE available_count >= :qty`, not a read-modify-write — eliminating a class of race condition where a losing optimistic-lock transaction could roll back a booking while the Redis seat stayed decremented. |
| **TOCTOU guard** | Availability is checked once before acquiring the reservation lock, then re-checked *inside* the lock before committing, closing the classic check-then-act race window. |
| **Optimistic locking** | `@Version` on `Booking`, `TicketTier`, and `Event`; `ObjectOptimisticLockingFailureException` maps to a clean `409` rather than a raw `500`. |
| **State machine** | An 11-state Spring State Machine (`@EnableStateMachineFactory`, one instance per request) governs the booking lifecycle, preventing invalid transitions such as checking in a cancelled booking. |
| **Idempotency** | Stripe webhook delivery is deduplicated via a database `UNIQUE` constraint (not an `existsBy…` pre-check, which is race-prone under concurrent delivery); booking creation requires a client-supplied `Idempotency-Key` header. |

### Reliability

- **Multi-replica-safe scheduling** — the reservation-expiry job acquires a distributed lock before running, so it's safe to run the same deployment across multiple backend instances without double-processing.
- **Structured, traceable logging** — a correlation ID propagates from the inbound HTTP request through every service log line via MDC, so a single request can be traced end-to-end in the logs.
- **Async offloading** — QR code generation and transactional emails are dispatched through RabbitMQ (with dead-letter queues) rather than blocking the request thread.

---

## Tech Stack

### Backend

| Layer | Technology |
| :--- | :--- |
| Language / Runtime | Java 21 (LTS) |
| Framework | Spring Boot 3.5, Spring Security, Spring Data JPA |
| State Management | Spring State Machine 4.0 |
| Payments | Stripe Java SDK 23.3 |
| Auth | JJWT 0.11 |
| API Docs | springdoc-openapi 2.8 (Swagger UI) |
| QR Generation | ZXing 3.5 |
| Coverage | JaCoCo 0.8 (80% instruction gate) |
| Unit & Integration Testing | JUnit 5 · Mockito · Testcontainers (real PostgreSQL, Redis, RabbitMQ in Docker) |
| Load & Performance Testing | k6 (Grafana Labs) |

### Data & Messaging

| Layer | Technology |
| :--- | :--- |
| Primary Datastore | PostgreSQL 17 · Flyway (12 versioned migrations) |
| Cache / Distributed Locking | Redis 7 (Lettuce client) |
| Async Messaging | RabbitMQ 4 |

### Frontend

| Layer | Technology |
| :--- | :--- |
| Framework | Next.js 16 (App Router, TypeScript strict) |
| UI Runtime | React 19 |
| Styling | Tailwind CSS 4 |
| Server State | TanStack Query 5 |
| Client State | Zustand 5 |
| Forms & Validation | React Hook Form 7 · Zod 4 |
| Payments | Stripe.js |
| Testing | Vitest |

### Infrastructure & Deployment

| Concern | Technology |
| :--- | :--- |
| Containerization | Docker — multi-stage build (`eclipse-temurin:21-jdk` → `21-jre`, non-root, ~619MB) |
| Local Orchestration | Docker Compose (7 services, health-gated startup) |
| CI/CD | GitHub Actions — see [CI/CD Pipeline](#cicd-pipeline) |
| Backend Hosting | Railway |
| Frontend Hosting | Vercel |

---

## Domain & API Surface

```
com.ticketing
├── event/          Events, venues, categories, search
├── booking/         Reservation lifecycle, state machine, tickets
├── payment/         Stripe checkout, webhooks, refunds
├── inventory/        Redis-backed atomic seat counters
├── pricing/          Early-bird / group / surge pricing engine
├── notification/     Async email + QR generation consumers
├── user/             Authentication, JWT, roles
└── common/           Security, exception handling, correlation IDs, shared config
```

| Metric | Value |
| :--- | :--- |
| REST endpoints | 30 across 9 controllers |
| Role-restricted endpoints (`@PreAuthorize`) | 18 |
| Domain services | 21 |
| Repositories | 11 |
| API-boundary DTOs | 25 (JPA entities are never exposed through the API) |
| Database migrations | 12 (Flyway, immutable once applied) |

---

## Quality & Testing

| Metric | Result |
| :--- | :--- |
| Backend test suite | **194 / 194 passing** |
| Instruction coverage (JaCoCo) | **83%** (gate: 80% minimum) |
| Dedicated concurrency tests | 2 — a 100-thread/50-seat Redis floor-guard proof, and a second end-to-end test through the full reservation path (DB write included) |
| Integration tests | Real PostgreSQL, Redis, and RabbitMQ via Testcontainers — no mocked infrastructure in integration suites |
| `@WebMvcTest` security coverage | Every slice runs the real Spring Security filter chain (`addFilters=false` is never used, so `@PreAuthorize` is always exercised) |

Run locally:

```bash
./mvnw verify              # full backend suite + coverage gate
cd frontend && npm test    # frontend component/helper tests
```

---

## CI/CD Pipeline

GitHub Actions runs three independent jobs on every push and pull request to `main` and `develop` — this workflow only gates whether code merges, it never deploys anything itself.

| Job | What it does |
| :--- | :--- |
| **Backend — Maven Test + JaCoCo** | `./mvnw verify` — full test suite plus the coverage gate; the JaCoCo report is uploaded as a build artifact even when the job fails. |
| **Frontend — Vitest + Next.js Build** | `npm test` (Vitest), then `npm run build` using `NEXT_PUBLIC_API_URL` / `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` from GitHub Secrets. |
| **Repo Hygiene** | Fails the build if any `.env*` file, `target/`, `frontend/node_modules/`, or `frontend/.next/` is ever tracked in git. |

Deploys are handled independently of this workflow: Railway and Vercel each auto-deploy from `main` through their own native GitHub integration, entirely outside GitHub Actions.

---

## Environments

### Production

| Component | Provider | Notes |
| :--- | :--- | :--- |
| Frontend | **Vercel** | Auto-deploys from `main`; CSP `connect-src` derived from `NEXT_PUBLIC_API_URL` at build time |
| Backend | **Railway** | Single container, Docker image built from the repo `Dockerfile`; `SPRING_PROFILES_ACTIVE=prod` |
| Database | Railway-managed PostgreSQL | Reached via `DATABASE_URL` |
| Cache / Locks | Railway-managed Redis | |
| Messaging | CloudAMQP (managed RabbitMQ) | |
| Payments | Stripe (test mode) | |

All production configuration is environment-variable driven — no secrets or environment-specific
URLs are ever committed to source (verify via `.gitignore`: `.env` is excluded everywhere).

### Local Development

**Prerequisites:** Java 21 · Node.js 20 · Docker Desktop

```bash
# 1. Infrastructure
cp .env.example .env
docker-compose up -d postgres redis rabbitmq mailhog

# 2. Backend  (http://localhost:8088, Swagger at /swagger-ui/index.html)
./mvnw spring-boot:run

# 3. Frontend (http://localhost:3000)
cd frontend
cp .env.example .env.local   # NEXT_PUBLIC_API_URL=http://localhost:8088
npm install && npm run dev
```

| Service | Port | Purpose |
| :--- | :--- | :--- |
| Backend API | `8088` | Spring Boot application |
| PostgreSQL | `5432` | Primary datastore |
| Redis | `6379` | Inventory counters, locks, JWT denylist, rate limiting |
| RabbitMQ | `5672` / `15672` | AMQP / management UI |
| pgAdmin | `5050` | Database browser |
| Redis Commander | `8082` | Redis browser |
| Mailhog | `1025` / `8025` | SMTP capture / web UI |

No accounts are seeded by default — register via `/auth/register`. Stripe test card:
`4242 4242 4242 4242`, any future expiry, any 3-digit CVC.

---

## Performance

k6 load-test results (full methodology and raw numbers in [`PERFORMANCE.md`](PERFORMANCE.md)):

| Scenario | Result |
| :--- | :--- |
| Baseline read path (50 VUs) | p95 15.9ms, 0.00% error rate, 74.4 req/s |
| Booking creation under contention (20 VUs) | p95 55.4ms, 0 server errors, floor guard held at exactly the tier's true capacity |
| Inventory-pressure burst (100 VUs vs. an 8-seat tier) | Up to 270 req/s, 99.9%+ correctly rejected with `409`, **zero oversell, zero 5xx** across repeated runs |
| Capacity ramp — **live Railway**, 10→200 VUs over 16 min | 32,577 requests, **0 failed, 0 server errors**, read-path p95 held at **394ms** at peak 200 concurrent VUs against the single-replica production deployment |

---

## Security Posture

Led by the control, not the threat — every row below is an implemented and verified mechanism in
this codebase, not an aspirational claim. Access control specifically uses **two independent
layers**: RBAC decides whether a role may call an endpoint at all; object-level authorization
separately decides whether the specific resource requested belongs to the caller. Neither
substitutes for the other.

| Control | Defends Against |
| :--- | :--- |
| **Parameterized queries (JPA/JPQL only)** | SQL Injection — zero native or string-concatenated SQL queries anywhere in the codebase. |
| **React JSX auto-escaping · JSON-only API · frontend CSP** | Cross-Site Scripting (XSS) — no server-rendered HTML templates, `dangerouslySetInnerHTML` is never used, and a `Content-Security-Policy` restricts script sources as defense-in-depth. |
| **Stateless JWT auth (no ambient cookies)** | Cross-Site Request Forgery (CSRF) — the API is stateless (`SessionCreationPolicy.STATELESS`) and authenticated via a Bearer token a cross-site page cannot silently attach, so CSRF's underlying attack vector doesn't exist here. Spring Security's CSRF filter is deliberately disabled — the correct configuration for a token-authenticated REST API, not an oversight. |
| **Role-Based Access Control (RBAC)** | Unauthorized endpoint access — `@PreAuthorize` role checks (`USER` / `ORGANIZER` / `ADMIN`) gate 18 of 30 endpoints. |
| **Object-level authorization** | Insecure Direct Object References / Broken Object Level Authorization — RBAC alone only proves a role may call an endpoint, not that a specific resource belongs to the caller. Booking read/cancel operations separately re-validate `booking.getUser().getId().equals(requestingUserId)` before returning or mutating data. |
| **Server-side role validation on registration** | Privilege Escalation — `Role.ADMIN` is explicitly rejected in `AuthService.register()` regardless of what a client sends, not merely omitted from a form. |
| **JWT `jti` + Redis denylist** | Session/Token Hijacking — logout immediately revokes the token's `jti` rather than waiting out its natural expiry. |
| **Redis Lua rate limiting** | Brute Force / Credential Stuffing — atomic `INCR`+`EXPIRE` caps auth attempts at 10/minute/IP in production. |
| **`X-Frame-Options: DENY`** | Clickjacking. |
| **`X-Content-Type-Options: nosniff`** | MIME-Sniffing. |
| **HSTS (1yr, includeSubDomains)** | Transport Downgrade / MITM. |
| **BCrypt password hashing** | Credential exposure on data breach — passwords are never stored or logged in plaintext. |
| **Bean Validation at the controller boundary** | Malformed or malicious input reaching services or repositories. |
| **Exact-origin CORS** | Cross-origin abuse — never a wildcard. |
| **`ADMIN`-only actuator** | Sensitive endpoint exposure — every `/actuator/**` route requires `ADMIN` except `/health`. |
| **Environment-variable-driven configuration** | Secret leakage — no hardcoded secrets anywhere in source (verified by repository-wide scan); `.env` is git-ignored everywhere; Stripe runs in test mode only (`sk_test_` / `pk_test_`). |

Full fix-by-fix security and reliability audit trail: [`PROGRESS.md`](PROGRESS.md).

---

## Star This Repo

<div align="center">

If this project was useful or interesting, a star ⭐ helps others find it.

**[Live Frontend](https://event-ticketing-platform-nu.vercel.app)** · **[Live API](https://backend-production-8daea.up.railway.app)** · **[API Docs](https://backend-production-8daea.up.railway.app/swagger-ui/index.html)** · **[Source](https://github.com/Sharawey74/Event-Ticketing-Platform)**

Licensed under the [MIT License](LICENSE).

</div>
