<!--
Sync Impact Report
- Version change: N/A -> 1.0.0
- Modified principles:
  - Template Principle 1 -> I. Domain-First Modular Monolith
  - Template Principle 2 -> II. Deterministic Concurrency and Inventory Safety
  - Template Principle 3 -> III. Time, Data Integrity, and Payment Correctness
  - Template Principle 4 -> IV. Test-First Delivery and Reliability Gates
  - Template Principle 5 -> V. Observability, Security, and Maintainability by Default
- Added sections:
  - Non-Negotiable Technical Standards
  - Delivery Workflow and Quality Gates
- Removed sections:
  - None
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
  - ⚠ pending: .specify/templates/commands/*.md (directory not present in repository)
- Follow-up TODOs:
  - None
-->

# Event Ticketing Platform Constitution

## Core Principles

### I. Domain-First Modular Monolith

The system MUST be implemented as a modular monolith with strict domain boundaries under
com.ticketing.{domain}. Every class MUST be placed in its domain package or shared common
package by responsibility. Controllers MUST stay thin and delegate business logic to services.
Rationale: preserving extractable domain boundaries now reduces future microservice migration risk.

### II. Deterministic Concurrency and Inventory Safety

Inventory-affecting operations MUST enforce a two-layer safety model: Redis distributed lock
(atomic SET NX EX) acquired before transactional work, and optimistic locking via @Version on
Booking and TicketTier entities. Reservation logic MUST re-check availability inside the lock body,
and Redis decrement/increment operations MUST be atomic Lua scripts that prevent negative stock.
Rationale: preventing oversell is a core correctness requirement for a ticketing platform.

### III. Time, Data Integrity, and Payment Correctness

All persisted timestamps MUST use Instant in code and TIMESTAMPTZ in PostgreSQL. Role fields MUST
use PostgreSQL ENUM types, bookings MUST use soft delete via deleted_at, and webhook idempotency
MUST be guaranteed by database uniqueness constraints instead of pre-check queries. Stripe webhook
controllers MUST NOT be transactional and may return HTTP 200 only after service transaction commit.
Rationale: UTC-safe time handling and database-enforced invariants prevent hidden production failures.

### IV. Test-First Delivery and Reliability Gates

Development MUST follow red-green-refactor for every feature slice. Unit tests MUST use JUnit 5 and
Mockito in isolation; integration tests MUST run with Spring Boot and Testcontainers using real
PostgreSQL, Redis, and RabbitMQ services. Concurrency-critical flows MUST include thread contention
tests, including the 100-thread and 50-seat booking scenario before related work is considered done.
Rationale: reliability in concurrent purchase paths cannot be validated by unit tests alone.

### V. Observability, Security, and Maintainability by Default

All request and message flows MUST propagate X-Correlation-ID through MDC and transport headers.
Dependency injection MUST use constructor injection with private final fields and no field injection.
Public API boundaries MUST use DTOs, sensitive endpoints MUST enforce @PreAuthorize, and business
rules MUST come from BusinessConstants instead of magic numbers.
Rationale: diagnosability and policy consistency are required for safe production operations.

## Non-Negotiable Technical Standards

- Runtime stack MUST remain aligned to the Phase 1A baseline: Java 21, Spring Boot 3.x,
  PostgreSQL 17, Redis 7, RabbitMQ 4.2.5, Stripe SDK 23.3.0, Spring State Machine 3.2.0,
  JJWT 0.11.5, and Next.js 14 frontend.
- Scheduler jobs MUST acquire distributed locks before execution in multi-replica environments.
- State machine configuration MUST use @EnableStateMachineFactory, not shared singleton state
  machine configuration.
- QR generation MUST be asynchronous through ticket.generation.queue and never block payment
  webhook request completion.
- Secrets MUST be environment-managed only; no credentials or keys may be hardcoded.

## Delivery Workflow and Quality Gates

- For each implementation session, engineers MUST cross-reference the base Phase 1A plan and
  adjustments overlay, then apply CRITICAL fixes before any lower-severity changes.
- IMPORTANT fixes MUST be completed in the same planned day or section. GOOD PRACTICE fixes MAY be
  deferred within a week but MUST close by the weekly checkpoint.
- Each completed module MUST satisfy: compile success, relevant tests green, and updated
  documentation where behavior or operational guidance changed.
- Weekly checkpoints (Day 7, Day 14, Day 21) MUST include explicit review of fix coverage against
  the project final checklist.

## Governance

This constitution is the authoritative project policy and supersedes conflicting local practices.
Every pull request and review MUST include an explicit constitution compliance check.

Amendment process:

1. Submit a documented proposal describing intent, impact, and migration implications.
2. Obtain approval from project maintainers before merging.
3. Update dependent templates and guidance files in the same change set.

Versioning policy:

- MAJOR: breaking governance changes or principle removals/redefinitions.
- MINOR: new principle or materially expanded mandatory guidance.
- PATCH: clarifications, wording improvements, and non-semantic edits.

Compliance review expectations:

- Planning artifacts MUST include constitution gates and explicit pass or fail status.
- Task plans MUST reflect required testing discipline and concurrency coverage.
- Runtime documentation MUST remain aligned with current governance constraints.

**Version**: 1.0.0 | **Ratified**: 2026-04-04 | **Last Amended**: 2026-04-13
