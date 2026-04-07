# Plans — Quick Reference

This folder contains all planning, session-management, and reference materials for the Event Ticketing Platform Phase 1A.

## Directory Structure

```
Plans/
├── session-prompts/          ← Pre-written session starters — one per day
│   ├── day-01-project-init.md
│   ├── day-02-event-service-auth.md
│   ├── day-03-venue-category-search.md
│   ├── day-04-nextjs-home-page.md
│   ├── day-05-inventory-redis.md
│   ├── day-06-n1-fixes-integration-tests.md
│   ├── day-07-week1-cleanup.md
│   ├── day-08-booking-state-machine.md
│   ├── day-09-stripe-webhook.md
│   └── day-10-rabbitmq-notifications.md
│
├── skills/                   ← Copilot skill files (copied from awesome-copilot)
│   ├── README.md             ← When to use each skill
│   ├── java-springboot.SKILL.md      ← PRIMARY — attach every backend session
│   ├── java-junit.SKILL.md           ← Day 16+ testing sessions
│   ├── postgresql-optimization.SKILL.md  ← Day 6, Day 16
│   ├── sql-optimization.SKILL.md     ← Days 1, 5, 6 (Flyway work)
│   ├── multi-stage-dockerfile.SKILL.md   ← Day 3, Day 7
│   └── conventional-commit.SKILL.md  ← Every commit
│
├── Text/                     ← Full plan text (extracted from PDFs)
│   ├── Phase1A_Section 2_ExecutionMap.txt          ← ALL day schedules
│   ├── Phase1A_Sections 3,4,5_FullStructure.txt   ← Architecture + DB + API
│   ├── Phase1A_Sections 6,7,8,9_ImplementationGuides.txt  ← Redis/RabbitMQ/SM/Stripe
│   ├── Phase1A_Sections 10,11,12_Testing_Deployment_Fundamentals.docx.txt
│   └── Phase1A_Sections 13,14,15,16_Practices_Resources_Troubleshooting_Transition.txt
│
├── PDF/                      ← Original PDF sources
│
├── intsructions.txt          ← SESSION SYSTEM PROMPT — paste as first message every session
└── Phase1A_Adjustments_and_Fixes.md  ← FIXES OVERLAY — attach every session
```

## Session Setup (Every Session)

```
ALWAYS DO THIS:
1. Open GitHub Copilot Chat
2. Select the appropriate SpecKit agent from dropdown
3. Paste Plans/intsructions.txt as your FIRST message
4. Attach Plans/Phase1A_Adjustments_and_Fixes.md
5. Attach the relevant Plans/Text/*.txt for today's plan section
6. [Backend implement sessions] Attach Plans/skills/java-springboot.SKILL.md
7. Open the day's session-prompts/day-XX-*.md — paste its content as your message

MAX 3 file attachments per session:
  - intsructions.txt (paste, don't attach)
  - Phase1A_Adjustments_and_Fixes.md (always attach)
  - Relevant Text plan section (attach the one matching today's work)
  - java-springboot.SKILL.md (attach for backend implement sessions)
```

## Plan Text Files — When to Attach Which

| Day                                       | Attach This Text File                                           |
| ----------------------------------------- | --------------------------------------------------------------- |
| All days (day schedule)                   | `Phase1A_Section 2_ExecutionMap.txt`                            |
| Days 1–3 (schema, architecture)           | `Phase1A_Sections 3,4,5_FullStructure.txt`                      |
| Days 5–12 (Redis/RabbitMQ/SM/Stripe)      | `Phase1A_Sections 6,7,8,9_ImplementationGuides.txt`             |
| Days 16–18 (testing, Docker, CI/CD)       | `Phase1A_Sections 10,11,12_Testing_Deployment_Fundamentals.txt` |
| Days 19–21 (clean code, k6, troubleshoot) | `Phase1A_Sections 13,14,15,16_Practices_Resources.txt`          |

> Attach Section 2 PLUS the relevant section for the day's feature. Never attach all 5.
