# End-to-End Development Workflow
## Event Ticketing Platform — Phase 1A

> **This is the single reference document for running every session.**
> Read once, bookmark, and follow exactly the same process every day.

---

## CRITICAL CLARIFICATIONS (Read First)

### Q1: Does SpecKit automatically create `features/` and its files?

**YES — SpecKit agents CREATE files for you. You do NOT create them manually.**

Here's the exact breakdown of what each agent creates:

| Agent | Creates | Location |
|-------|---------|----------|
| `speckit.plan` | `plan.md` | `.specify/features/{feature-name}/plan.md` |
| `speckit.tasks` | `tasks.md` | `.specify/features/{feature-name}/tasks.md` |
| `speckit.implement` | Source code files | `src/main/java/com/ticketing/...` |
| `speckit.checklist` | Validation table (in chat) | No file saved — it writes to chat |
| `speckit.constitution` | `constitution.md` | `.specify/memory/constitution.md` |

**The ONE thing you must create manually is the feature folder name:**

```powershell
# Run from project root BEFORE calling speckit.plan:
.\.specify\scripts\powershell\create-new-feature.ps1 -FeatureName "event-crud"
# This creates .specify/features/event-crud/ (empty folder)
# Then speckit.plan fills it with plan.md, and speckit.tasks adds tasks.md
```

---

### Q2: Which SpecKit agents do I actually use (given my plans are already fully written)?

Since your plans (5 Text files + Phase1A_Adjustments_and_Fixes.md) are already written in complete detail —
including method signatures, package paths, SQL schemas, and test names — here is the **honest,
practical answer** on which agents provide real value vs which would just rewrite what you already have:

#### ✅ Use These Agents

| Agent | Use? | When | Why |
|-------|------|------|-----|
| `speckit.constitution` | **YES — ONCE** | Before Day 1 | Generates the project memory (`.specify/memory/constitution.md`) that all other agents reference. Run it once. |
| `speckit.plan` | **YES — per feature** | Start of each feature | Feeds your text plan into `.specify/features/{name}/plan.md` in SpecKit format. Required input for `speckit.tasks`. |
| `speckit.tasks` | **YES — per feature** | After `speckit.plan` | Converts `plan.md` into an atomic, checkable `tasks.md`. This is what `speckit.implement` reads to generate code. |
| `speckit.implement` | **YES — primary agent** | Implementation blocks | The main coding agent. Reads `tasks.md` + `constitution.md` + instructions → generates code + marks tasks `[x]`. |
| `speckit.checklist` | **YES — end of session** | After `speckit.implement` | Validates completeness. Reports: which tasks done, which tests missing, which fixes unapplied. |

#### ❌ Skip These Agents (Given Your Current Setup)

| Agent | Skip? | Why |
|-------|-------|-----|
| `speckit.clarify` | **SKIP** | For when requirements are ambiguous. Yours are not — you have fully detailed plans with method signatures. Running clarify just repeats your plan back at you. |
| `speckit.specify` | **SKIP** | Writes `requirements.md` from scratch. You already have Section 3–9 text files that are your requirements. Specifying again is redundant. |
| `speckit.analyze` | **OPTIONAL** | Useful ONLY on Day 8 (state machine) or Day 11 (concurrent booking test) where the existing code patterns matter. Elsewhere, your plan section replaces it. |

#### Your Optimal Agent Chain (Every Day)

```
BEFORE DAY 1 (one-time only):
  speckit.constitution → reads intsructions.txt + plan sections → writes constitution.md

EACH FEATURE (Days 1–21):
  1. create-new-feature.ps1 -FeatureName "{today's feature}"
  2. speckit.plan          → writes plan.md (feed it today's session-prompts/day-XX file)
  3. speckit.tasks         → writes tasks.md from plan.md
  4. speckit.implement     → generates code, marks tasks [x]
                             (attach java-springboot.SKILL.md here)
  5. speckit.checklist     → end-of-session validation
```

---

## THE SESSION WORKFLOW (Step-by-Step)

### PHASE 0 — One-Time Setup (Do Before Day 1)

```
1. Open VS Code in project root
2. Open GitHub Copilot Chat panel (Ctrl+Shift+I)
3. From agent dropdown → select: SpecKit
4. Type: @speckit.constitution
5. Attach: Plans/intsructions.txt
6. Attach: Plans/Phase1A_Adjustments_and_Fixes.md
7. Type: "Generate the project constitution from these documents"
8. Copilot writes: .specify/memory/constitution.md
9. Review constitution.md — verify Java 21, package names, architecture decisions are correct
10. Commit: chore: generate project constitution
```

---

### PHASE 1 — Session Start (Every Day)

#### Step 1.1 — Create the Feature Folder

```powershell
# From project root terminal:
.\.specify\scripts\powershell\create-new-feature.ps1 -FeatureName "event-crud"
# Day-specific names:
# Day 1:  "project-initialization"
# Day 2:  "event-crud"
# Day 5:  "inventory-service-redis"
# Day 8:  "booking-state-machine"
# Day 9:  "stripe-payment"
# Day 10: "rabbitmq-notifications"
# etc.
```

#### Step 1.2 — Open the Day's Session Prompt

Open: `Plans/session-prompts/day-XX-{name}.md`

Read it fully BEFORE opening chat. Identify:
- Which **plan Text file** to attach (see the "Active Plan Reference" section in each prompt)
- Which **fixes** are active today (table in each prompt)
- Which **skills** to attach (listed at bottom of each prompt)

---

### PHASE 2 — Plan Phase (`speckit.plan`)

```
1. In Copilot Chat dropdown → select: @speckit.plan
2. PASTE (not attach) the content of Plans/intsructions.txt into chat
3. Attach: Plans/Phase1A_Adjustments_and_Fixes.md
4. Attach: Plans/Text/{relevant section file}     ← see day's session-prompt
5. Type this exact message:

   "We are working on Day {N} — {theme}. 
   Feature: {feature-name}. 
   Read the execution map for Day {N} in the attached plan file.
   Cross-check all fixes tagged for Day {N} in the Adjustments overlay.
   Generate a SpecKit plan.md for this feature."

6. speckit.plan creates: .specify/features/{feature-name}/plan.md
7. Review plan.md — verify it includes all Day {N} tasks + all fixes from overlay
```

---

### PHASE 3 — Tasks Phase (`speckit.tasks`)

```
1. In Copilot Chat dropdown → select: @speckit.tasks
2. Type:
   "Generate tasks from the plan for feature {feature-name}"
3. speckit.tasks reads plan.md → creates: .specify/features/{feature-name}/tasks.md
4. Review tasks.md — every task should be atomic, testable, and checkable
5. Verify: all 🔴 CRITICAL fix items appear as [ ] tasks at the TOP of tasks.md
   If any critical fix is missing → manually add it before proceeding
```

---

### PHASE 4 — Implementation Phase (`speckit.implement`)

**This is the primary session work. Repeat as many times as needed.**

```
1. In Copilot Chat dropdown → select: @speckit.implement
2. Attach: Plans/skills/java-springboot.SKILL.md    ← for all backend sessions
3. Type:
   "Implement the next uncompleted task in .specify/features/{feature-name}/tasks.md.
   Apply all fixes from the adjustment overlay.
   Use constructor injection (@RequiredArgsConstructor) throughout.
   All timestamps must use Instant (UTC), never LocalDateTime."

4. speckit.implement:
   - Reads tasks.md for the next [ ] task
   - Reads constitution.md for project context
   - Reads .github/copilot-instructions.md for project standards
   - Reads .github/instructions/*.instructions.md (auto-injected per file type)
   - Generates the code
   - Marks the task [x] in tasks.md

5. After each task:
   - Review the generated code carefully
   - Run: ./mvnw compile (must pass before moving to next task)
   - Run any tests related to that task

6. If the task has a 🔴 CRITICAL fix attached:
   - Do NOT move to the next task until the critical fix is confirmed in the code
   - Verify: the fix is present AND the relevant test passes

7. Repeat from step 1 until all [ ] tasks in tasks.md are [x]
```

#### Implementation Anti-Patterns to Watch For

| If Copilot generates this → | Immediately correct to this |
|-----|-----|
| `@Autowired private EventRepository repo;` | `@RequiredArgsConstructor` + `private final EventRepository repo;` |
| `LocalDateTime.now()` | `Instant.now()` |
| `SETNX + EXPIRE` (two Redis calls) | `setIfAbsent(key, value, Duration.ofSeconds(ttl))` |
| `@EnableStateMachine` | `@EnableStateMachineFactory` |
| `redisTemplate.opsForValue().decrement(key)` | Lua script: check + decrement atomically |
| Raw string `"RESERVED"` in code | `BookingState.RESERVED` (enum) |
| Magic number `300` for lock TTL | `BusinessConstants.RESERVATION_TTL_SECONDS` |
| `@Transactional` on `StripeWebhookController` | Remove — webhook controller must NOT be transactional |

---

### PHASE 5 — Validation Phase (`speckit.checklist`)

```
1. In Copilot Chat dropdown → select: @speckit.checklist
2. Type:
   "Run the full checklist for Day {N} — {theme}.
   Check: all tasks completed, all 🔴 fixes applied, all tests passing."
3. speckit.checklist validates:
   - tasks.md: all [x]?
   - All 🔴 CRITICAL fixes in the overlay: applied?
   - ./mvnw test: all green?
   - Code quality: no magic numbers, correct injection style?
4. Any [ ] remaining tasks → return to Phase 4
5. Any test failures → fix before closing the session
```

---

### PHASE 6 — Session Close (Every Day)

#### Step 6.1 — Mandatory Handoff

At end of EVERY session, request this from Copilot:

```
"Print the session handoff summary in this exact format:
--- SESSION HANDOFF ---
Day N status     : [In Progress / Complete]
Fixes applied    : [list every fix ID from overlay]
Fixes pending    : [list any deferred fixes]
Files created    : [full list]
Files modified   : [full list]
Next session start: [exact first task for next session]
-----------------------"
```

Save this output to: `Docs/AI_Sessions/day-{N}-handoff.md`

#### Step 6.2 — Git Commit

```bash
# Run conventional commit skill:
git add .
git status
# Commit message format: type(scope): description
# Examples:
git commit -m "feat(event): implement event crud with organizer authorization"
git commit -m "feat(inventory): implement redis inventory service with lua floor guard"
git commit -m "feat(booking): implement booking state machine with all transitions"
git commit -m "perf(event): fix n+1 queries using entity graph"
```

#### Step 6.3 — Update Weekly Hours Log

Track in the repo root `PROGRESS.md`:
```markdown
| Day | Date | Hours | Status | Key Deliverable |
| 1   | Apr 4 | 8h  | ✅ Complete | Project scaffold, 9 migrations |
```

---

## FULL PROJECT STRUCTURE (Final)

```
Event-Ticketing-Platform/
│
├── .github/                          ← GitHub Copilot configuration
│   ├── copilot-instructions.md       ← ✅ MASTER config — always active
│   ├── instructions/                 ← ✅ Auto-injected per file type
│   │   ├── springboot.instructions.md
│   │   ├── security-and-owasp.instructions.md
│   │   ├── spec-driven-workflow-v1.instructions.md
│   │   ├── object-calisthenics.instructions.md
│   │   ├── oop-design-patterns.instructions.md
│   │   ├── self-explanatory-code-commenting.instructions.md
│   │   ├── update-docs-on-code-change.instructions.md
│   │   ├── performance-optimization.instructions.md
│   │   └── nextjs.instructions.md
│   ├── agents/                       ← SpecKit agents (9 agents)
│   │   ├── speckit.analyze.agent.md
│   │   ├── speckit.plan.agent.md
│   │   ├── speckit.tasks.agent.md
│   │   ├── speckit.implement.agent.md
│   │   ├── speckit.checklist.agent.md
│   │   ├── speckit.clarify.agent.md
│   │   ├── speckit.constitution.agent.md
│   │   ├── speckit.specify.agent.md
│   │   └── speckit.taskstoissues.agent.md
│   └── prompts/                      ← SpecKit prompt templates
│       └── speckit.*.prompt.md (9 files)
│
├── .specify/                         ← SpecKit project memory
│   ├── memory/
│   │   └── constitution.md           ← ✅ Generated by speckit.constitution
│   ├── features/                     ← SpecKit CREATES these during sessions
│   │   └── {feature-name}/
│   │       ├── plan.md               ← Created by speckit.plan
│   │       └── tasks.md              ← Created by speckit.tasks
│   ├── templates/                    ← SpecKit scaffolding templates
│   ├── scripts/                      ← create-new-feature.ps1 etc.
│   └── integrations/                 ← Copilot integration manifests
│
├── Plans/                            ← YOUR planning materials
│   ├── README.md                     ← ✅ Session setup guide
│   ├── END_TO_END_WORKFLOW.md        ← ✅ This file
│   ├── intsructions.txt              ← ✅ System prompt — paste every session
│   ├── Phase1A_Adjustments_and_Fixes.md  ← ✅ Overlay — attach every session
│   ├── session-prompts/              ← ✅ Pre-written prompts Days 1–10
│   │   ├── day-01-project-init.md
│   │   ├── day-02-event-service-auth.md
│   │   ├── day-03-venue-category-search.md
│   │   ├── day-04-nextjs-home-page.md
│   │   ├── day-05-inventory-redis.md
│   │   ├── day-06-n1-fixes-integration-tests.md
│   │   ├── day-07-week1-cleanup.md
│   │   ├── day-08-booking-state-machine.md
│   │   ├── day-09-stripe-webhook.md
│   │   └── day-10-rabbitmq-notifications.md
│   ├── skills/                       ← ✅ Skill files for manual attachment
│   │   ├── README.md                 ← When to attach which skill
│   │   ├── java-springboot.SKILL.md  ← Attach every backend session
│   │   ├── java-junit.SKILL.md
│   │   ├── postgresql-optimization.SKILL.md
│   │   ├── sql-optimization.SKILL.md
│   │   ├── multi-stage-dockerfile.SKILL.md
│   │   └── conventional-commit.SKILL.md
│   └── Text/                         ← Full plan text files (5 sections)
│
├── Docs/
│   └── AI_Sessions/                  ← Save session handoff summaries here
│
├── src/                              ← Spring Boot source (built Day 1+)
├── frontend/                         ← Next.js 14 source (built Day 4+)
├── docker-compose.yml
├── pom.xml
└── PROGRESS.md                       ← Weekly hours log
```

---

## ATTACHMENT RULES (Per Session)

### Maximum 3 file attachments per session

| Priority | File | Type | How |
|----------|------|------|-----|
| 🔴 Always | `Plans/intsructions.txt` | PASTE (don't attach) | First message text |
| 🔴 Always | `Plans/Phase1A_Adjustments_and_Fixes.md` | ATTACH | Drag-drop or #file: |
| 🟡 Backend session | `Plans/skills/java-springboot.SKILL.md` | ATTACH | Drag-drop or #file: |
| 🟡 Backend session | `Plans/Text/{relevant section}` | ATTACH | Drag-drop or #file: |
| 🟢 Optional | Other skill SKILL.md | ATTACH | Only 1 extra skill max |

### Which Text file to attach per day:

| Days | Attach |
|------|--------|
| All days (schedule ref) | `Phase1A_Section 2_ExecutionMap.txt` |
| Days 1–3 (schema, packages, API spec) | `Phase1A_Sections 3,4,5_FullStructure.txt` |
| Days 5–12 (Redis, RabbitMQ, State Machine, Stripe) | `Phase1A_Sections 6,7,8,9_ImplementationGuides.txt` |
| Days 16–18 (testing, Docker, CI/CD) | `Phase1A_Sections 10,11,12_Testing_Deployment_Fundamentals.txt` |
| Days 19–21 (clean code, k6, troubleshoot) | `Phase1A_Sections 13,14,15,16_Practices_Resources.txt` |

> **Rule:** Attach Section 2 + the relevant feature section. Never attach all 5 at once.

---

## SEVERITY ENFORCEMENT (Non-Negotiable)

| Severity | Rule |
|----------|------|
| 🔴 CRITICAL | Applied BEFORE moving to the next task. Zero exceptions. |
| 🟡 IMPORTANT | Applied within the same day it's listed. Not deferred. |
| 🟢 GOOD PRACTICE | Applied before end-of-day. Can be deferred to end of session. |

If `speckit.implement` generates code that conflicts with a 🔴 CRITICAL fix, **stop immediately**.
Correct the code before generating the next task.

---

## PRE-DAY 1 CHECKLIST

Before opening any session prompt, confirm these are done:

```
[ ] .specify/memory/constitution.md exists (run speckit.constitution once)
[ ] Stripe test account: sk_test_* and pk_test_* saved to application-local.yml
[ ] Stripe CLI installed: stripe login done
[ ] Railway account created (Fix PW3-1 from Phase1A_Adjustments_and_Fixes.md)
[ ] Java version confirmed as 21 in pom.xml, intsructions.txt, and constitution.md
[ ] Docker Desktop running
[ ] Plans/README.md reviewed — know which Text file to attach today
```
