# Day 7 — Session Prompt
**Date:** Friday, April 10, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 7 — Week 1 Cleanup + Docker Compose Polish.
Feature: week1-cleanup

Active fixes today:
- Fix 7.1 — IMPORTANT: @Version on Booking and TicketTier for optimistic locking
- Fix CC-1 — GOOD: X-Correlation-ID on all log statements
- Fix CC-2 — IMPORTANT: BusinessConstants for all values

Pre-conditions confirmed:
- Day 6 complete: N+1 issues resolved and integration tests passing ✅
- Docker Desktop is RUNNING ✅

This is a Cleanup Session (No Java TDD required for new services).
Instead, confirm that running ./mvnw test passes the ENTIRE test suite with all fixes applied.

Non-negotiable rules:
- Validate postgres:17, redis:7, and rabbitmq:4-management are used in docker-compose.yml
- Validate @Version is correctly applied to Booking and TicketTier entities
- Zero @Autowired or LocalDateTime across the entire Week 1 codebase.

Start with: Apply Fix 7.1 to Booking and TicketTier entities, then review docker-compose.yml to ensure PostgreSQL 17, Redis 7, and RabbitMQ 4.2.5 are declared correctly.
```

---

## Context Briefing

**What we're building today:**
Day 7 is Week 1 cleanup. No new major features — this is the day to close all open items, polish Docker Compose to be fully production-like, apply the self-code-review checklist from Section 13, and verify the Week 1 checkpoint. Everything must be solid before entering Week 2 (the hard stuff).

**Why a cleanup day matters:**
Week 2 contains the most complex code in the entire project (state machine, Stripe, distributed locking). Entering Week 2 with technical debt from Week 1 will cause compounding problems — a failing concurrent booking test on Day 11 will be nearly impossible to debug if the event module has unresolved issues.

**Pre-conditions from Day 6:**
- Zero N+1 queries in Event module ✅
- Integration tests passing ✅
- EXPLAIN ANALYZE shows Index Scan ✅
- Full test suite green ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 7
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## No New Fixes Today
This is the Week 1 checkpoint. Verify all Fix-1.x and Fix-2.x fixes are applied.

---

## Tasks (In Order)

### Morning (2 hrs) — System Design Reading
- Read "Design Ticketmaster" (systemdesign.one)
- Draw architecture diagram: CDN, Load Balancer, API servers, DB primary/replica, Redis cluster, RabbitMQ
- Document: "What is the hardest problem in a ticketing system?" → Race conditions during high-concurrency purchase

### Afternoon (3 hrs)

#### Week 1 Code Review (Section 13 Checklist)
- Go through every file written this week
- Apply Section 13 clean code checklist
- No method > 20 lines? ✓, No magic numbers? ✓, All variables descriptive? ✓, No raw status strings? ✓

#### Docker Compose — Full Configuration
Add services:
- `pgAdmin` (port 5050) — DB browser
- `redis-commander` (port 8081) — Redis key inspector
- `mailhog` (SMTP 1025, UI 8025) — for email testing on Day 10
- RabbitMQ Management UI already at port 15672

Add health checks on all container services. Document all ports in README.

#### Polish Event Detail API
Update Event Detail response to use `@EntityGraph`:
- Include: event info, venue info (name, address, city, capacity), ticket tier info (name, price, available count, total capacity), organizer info (name, profile)
- All in a SINGLE query — verify with show-sql=true

### Evening (1 hr) — Week 1 Checkpoint Verification
Run through the Week 1 checkpoint:
```
[ ] Spring Boot project starts cleanly (mvnw spring-boot:run)
[ ] 9 Flyway migrations run successfully
[ ] Event CRUD: POST, GET, PUT, DELETE, Publish — all working
[ ] JWT auth: login + protected routes
[ ] Next.js home page renders real data
[ ] Zero N+1 queries in Event module
[ ] EventService: 7+ tests, all green
[ ] Docker Compose: postgres + redis + rabbitmq + spring boot in one command
```

Git: commit all Week 1 work. Push. Add Week 1 summary to GitHub PR.

---

## Expected Deliverable / Success Criteria

```
[ ] ALL Week 1 checkpoint items verified
[ ] Docker Compose starts 6+ services with one command
[ ] pgAdmin + redis-commander + mailhog accessible
[ ] Event Detail API: single query for all nested data
[ ] Self-code-review checklist applied to all Week 1 files
[ ] ./mvnw test — all tests green
[ ] GitHub repo: Week 1 summary commits pushed
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`
- `Plans/skills/multi-stage-dockerfile.SKILL.md`

## Week 1 → Week 2 Handoff Note
Before starting Day 8, confirm:
1. Stripe account created (sk_test_ + pk_test_ saved to application-local.yml)
2. Stripe CLI installed (`stripe login` done)
3. Railway account created (apply Fix PW3-1 if not done yet!)
