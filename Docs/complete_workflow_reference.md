# Complete Workflow Reference — Event Ticketing Platform
## The definitive guide to what goes where, what's automatic, and what you write yourself

---

## PART 1 — The 3 File Types Explained (`.instructions.md` vs `SKILL.md` vs `.agent.md`)

These are fundamentally different things. Do NOT mix them up.

---

### 🔵 `.instructions.md` — The Law (Auto-Enforced Rules)

**What it is:** A file of **non-negotiable coding rules** that GitHub Copilot reads automatically  
**When it activates:** Automatically, based on what file you're editing (controlled by `applyTo` in its frontmatter)  
**You never attach it. Copilot reads it silently.**

```
---
applyTo: '**/*.java, **/*.kt'      ← triggers on Java files
description: 'Spring Boot rules'
---

- Always use constructor injection
- Never use @Autowired
- All time fields use Instant, never LocalDateTime
```

**Real example in your project:**  
When `speckit.implement` generates `BookingService.java`, Copilot automatically 
applies `springboot.instructions.md` because the file is `.java`. You don't ask for 
it. You don't attach it. It's always on.

**Analogy:** Like ESLint rules. You don't run ESLint manually before every line — 
it enforces rules automatically.

---

### 🟢 `SKILL.md` — The Expert's Cheat Sheet (Manual Context)

**What it is:** A structured document of **best practices and knowledge** you attach when starting a complex session to give the AI extra domain expertise  
**When it activates:** ONLY when you manually attach it or reference it in chat  
**You attach it yourself when you need it.**

```
---
name: java-springboot
description: 'Get best practices for developing applications with Spring Boot.'
---

# Spring Boot Best Practices
- Use @ConfigurationProperties for type-safe config
- Use @EntityGraph to prevent N+1
- Use Testcontainers for integration tests
...
```

**Real example in your project:**  
Before implementing `DistributedLockService.java`, you open chat and drag-drop 
`java-springboot/SKILL.md` in. Now the AI has Spring Boot expertise as active 
context FOR THIS SESSION, on top of the auto-injected instructions.

**Analogy:** Like giving a contractor the project manual before they start work. 
You hand it to them. They don't receive it automatically.

**Is it ALWAYS a SKILL.md?** YES for this pattern.  
Skills are specifically designed for this "attach for context" use case.  
You could technically paste any `.md` content as context, but SKILL.md is the 
structured format for this exact purpose.

---

### 🔴 `.agent.md` — The Persona (The Copilot Mode)

**What it is:** A file that **transforms Copilot into a specific role** with specific behaviors, tools, and a structured step-by-step workflow  
**When it activates:** When you SELECT it from the Agent dropdown in VS Code Copilot chat  
**You choose it from the dropdown. It IS the prompt structure.**

```
---
description: Execute the implementation plan by processing tasks.md
tools: ['codebase', 'editFiles', 'run_terminal_command']
---

1. Run check-prerequisites.ps1 to find paths
2. Load tasks.md and plan.md
3. Execute each task in order
4. Mark [X] on completed tasks
5. Report progress
```

**Real example in your project:**  
You select `speckit.implement` from the dropdown. Copilot BECOMES a 
structured implementation executor. It reads your tasks.md, runs scripts, 
generates code, marks tasks complete — following the agent's internal steps 
automatically. You don't prompt it step by step.

**Analogy:** Like switching Copilot from "assistant mode" to "senior engineer mode". 
The agent file defines the engineer's job description and workflow.

---

### Side-by-Side Comparison

| Property | `.instructions.md` | `SKILL.md` | `.agent.md` |
|---|---|---|---|
| **Purpose** | Code quality rules | Domain best practices | Persona + workflow |
| **Activated by** | File pattern match (auto) | You attaching it in chat | You selecting from dropdown |
| **Requires action?** | ❌ Never | ✅ Manual attach | ✅ Manual selection |
| **Scope** | Every session, every file | This session only | This session only |
| **Writes to disk?** | ❌ No | ❌ No | ✅ Yes (tasks, specs, etc.) |
| **Knows your project?** | Only what files it sees | Only what you give it | ✅ Reads `.specify/` |
| **Example** | `springboot.instructions.md` | `java-springboot/SKILL.md` | `speckit.implement.agent.md` |

---

## PART 2 — Auto-Attached vs Manually Attached: The Complete List

### ✅ AUTOMATICALLY read by Copilot (you do nothing)

These files are read silently every session, no action required from you:

```
Event-Ticketing-Platform/
└── .github/
    ├── copilot-instructions.md          ← MASTER CONFIG — ALWAYS active
    └── instructions/                    ← Auto-injected based on applyTo pattern
        ├── springboot.instructions.md              (all *.java, *.kt)
        ├── spec-driven-workflow-v1.instructions.md (all files — applyTo: '**')
        ├── security-and-owasp.instructions.md      (all *.java)
        ├── performance-optimization.instructions.md (all *.java)
        ├── oop-design-patterns.instructions.md     (all *.java)
        ├── object-calisthenics.instructions.md     (all *.java)
        ├── self-explanatory-code-commenting.md     (all *.java)
        ├── update-docs-on-code-change.md           (all *.md, *.java)
        └── code-review-generic.instructions.md     (all *.java)
```

Also automatically read by SpecKit agents (not by you):
```
.specify/
├── memory/constitution.md    ← Read by speckit agents as permanent project memory
├── init-options.json         ← SpecKit project config
└── integration.json          ← SpecKit integration config
```

---

### 📎 MANUALLY attached by you in chat (you drag-drop or @-reference)

**Every session, always attach these:**

| File | Why |
|---|---|
| `Phase1A_Adjustments_and_Fixes.md` | Your fixes overlay — AI applies all 🔴🟡🟢 fixes into code |
| The relevant Plan PDF section | Source of truth for WHAT to build that day |
| `intsructions.txt` content | Paste as your FIRST MESSAGE to set session rules |

**Attach when starting an implementation session:**

| File | When |
|---|---|
| `skills/java-springboot/SKILL.md` | Every backend Java session (adds Spring Boot depth) |

**Situational — attach only when relevant:**

| File | When |
|---|---|
| A specific `.specify/features/*/spec.md` | When working on a defined feature spec |
| `.specify/features/*/tasks.md` | When running speckit.implement for a feature |
| `.specify/features/*/plan.md` | When reviewing or continuing a planned feature |

---

### 🗂️ Files SpecKit agents READ automatically (you don't attach)

When you select a SpecKit agent, it runs a script to find these and reads them itself:

```
.specify/
├── memory/constitution.md           ← Always loaded by all speckit agents
└── features/
    └── [feature-name]/
        ├── spec.md                  ← speckit.clarify, speckit.plan read this
        ├── plan.md                  ← speckit.tasks, speckit.implement read this  
        ├── tasks.md                 ← speckit.implement reads & updates this
        ├── requirements.md          ← speckit.analyze writes this
        └── checklists/              ← speckit.checklist reads these
```

The SpecKit agent runs `.specify/scripts/powershell/check-prerequisites.ps1` 
to discover all these paths automatically. That's why SpecKit doesn't need 
you to tell it where the files are.

---

## PART 3 — The Prompts Question: Write Them Yourself or Pre-Design Them?

### What are the `.github/prompts/*.prompt.md` files?

Look at what's actually inside them:
```markdown
---
agent: speckit.analyze
---
```

That's it. They are just **shortcuts** that launch an agent. The prompt content 
is inside the `.agent.md` file itself. The `.prompt.md` file is only a trigger.

### So what do YOU write?

**YES — you write your own prompts as the "argument" you give the agent.**

The agents accept `$ARGUMENTS` — the text you type after selecting the agent.  
This is where YOUR project-specific context goes.

**Level 1 — Minimal (what most people do):**
```
"We are working on Day 8 — BookingService.reserveTickets()"
```
The agent handles the rest.

**Level 2 — Practical (recommended for your project):**
```
"Day 8 — BookingService.reserveTickets()

Section: Section 8 of Phase 1A plan
Fixes to apply: Fix 8.1 (TOCTOU double-check), Fix 5.1 (Lua floor guard)
Priority fixes: 🔴 Fix 8.1 CRITICAL — must be applied before any other code

Context:
- DistributedLockService is already implemented from Day 5
- InventoryService.reserveSeat() uses Lua script (Fix 5.1 done)
- Booking state must be RESERVED with expiresAt = Instant.now() + 300s (Fix 1.1)

Start with ANALYZE phase — write EARS requirements for the full reserveTickets() flow."
```

**Level 3 — Pre-designed session prompts (your question about "practical and functional prompts"):**

YES. You CAN and SHOULD design reusable prompt templates for each day.  
Store them as `.md` files in your project:

```
Event-Ticketing-Platform/
└── Plans/
    └── session-prompts/
        ├── day-08-booking-service.md
        ├── day-09-stripe-payment.md
        └── day-05-redis-lock.md
```

Each file contains a structured, context-rich prompt following proper  
prompt engineering format that you paste when starting that day's session.

**Recommended Session Prompt Format:**
```markdown
## Session: Day 8 — BookingService

### Agent: speckit.analyze → speckit.plan → speckit.tasks → speckit.implement

### Context I am providing:
- Original Plan: [Section 8 excerpt]
- Active Fixes: Fix 8.1 (TOCTOU), Fix CC-1 (MDC), Fix CC-2 (BusinessConstants)
- Dependencies built: DistributedLockService ✅, InventoryService ✅

### EARS Requirements to produce:
WHEN a user calls reserveTickets()...
[pre-written requirements for the agent to validate/refine]

### Implementation constraints (from intsructions.txt):
- @RequiredArgsConstructor, no @Autowired
- Instant, never LocalDateTime  
- @Transactional on the method
- CorrelationId in all log statements (Fix CC-1)

### Expected output:
- .specify/features/booking-service/requirements.md
- .specify/features/booking-service/plan.md
- .specify/features/booking-service/tasks.md
```

---

## PART 4 — When to Attach the SKILL.md (Extra Context Question)

### Rule: Attach `java-springboot/SKILL.md` when generating IMPLEMENTATION code.

**Attach it when:**
- Starting `speckit.implement` for any backend Java class
- The session will generate >1 Java file
- Working on complex components (StateMachine, Redis, RabbitMQ consumers)

**Don't bother attaching it when:**
- Running `speckit.analyze` (it's planning, not coding)
- Running `speckit.clarify` (it's Q&A, not coding)  
- Running `speckit.checklist` (it's validation, not coding)
- Writing SQL migrations (Flyway files)

### Is it ALWAYS a SKILL.md?

**Yes, for the "extra context" slot, the answer is always SKILL.md.**  
That's what SKILL.md was specifically designed for — structured, reusable  
domain expertise you inject into a session manually.

The only exception is if you want to attach a whole document that isn't  
packaged as a skill (like a specific diagram or API spec file) — then  
you drag-drop that file directly. But for reusable expert knowledge,  
SKILL.md is the format.

---

## PART 5 — The Complete Session Checklist

### Before every session:

```
SETUP (one-time, already done):
✅ .github/instructions/ has all instruction .md files
✅ .github/agents/ has all speckit agent files
✅ .github/copilot-instructions.md exists

START OF SESSION:
1. Paste intsructions.txt content as first message
2. Attach Phase1A_Adjustments_and_Fixes.md
3. Attach relevant Plan PDF section for the day
4. Select the appropriate speckit agent from dropdown

IF IMPLEMENTING CODE:
5. Also attach skills/java-springboot/SKILL.md

YOUR ARGUMENT TO THE AGENT:
6. Type or paste your session prompt (Day N + fixes + context)
```

### Copilot handles automatically:
- Reading all `.instructions.md` files
- Reading `.github/copilot-instructions.md`
- SpecKit reading `.specify/memory/constitution.md`
- SpecKit running check-prerequisites.ps1 to find spec files
- Applying `applyTo` file pattern matching

---

## Summary Table — Everything in One View

| File/Resource | Type | Auto or Manual | Purpose |
|---|---|---|---|
| `.github/copilot-instructions.md` | Instructions | 🤖 Auto | Master project rules, always active |
| `springboot.instructions.md` | Instructions | 🤖 Auto | Spring Boot coding rules on *.java |
| `security-and-owasp.instructions.md` | Instructions | 🤖 Auto | Security rules on *.java |
| `performance-optimization.instructions.md` | Instructions | 🤖 Auto | Performance rules on *.java |
| `spec-driven-workflow-v1.instructions.md` | Instructions | 🤖 Auto | Spec lifecycle rules on all files |
| `oop-design-patterns.instructions.md` | Instructions | 🤖 Auto | OOP rules on *.java |
| `java-springboot/SKILL.md` | Skill | 📎 Manual | Spring Boot depth for impl sessions |
| `speckit.analyze.agent.md` | Agent | 👆 Dropdown | Runs ANALYZE phase with EARS notation |
| `speckit.clarify.agent.md` | Agent | 👆 Dropdown | Removes spec ambiguity |
| `speckit.plan.agent.md` | Agent | 👆 Dropdown | Writes design.md and plan |
| `speckit.tasks.agent.md` | Agent | 👆 Dropdown | Breaks plan into tasks.md |
| `speckit.implement.agent.md` | Agent | 👆 Dropdown | Executes tasks.md |
| `speckit.checklist.agent.md` | Agent | 👆 Dropdown | Validates completion |
| `.specify/memory/constitution.md` | Memory | 🤖 Auto (by agent) | Permanent project principles |
| `intsructions.txt` | Session rules | 📎 Manual (paste) | Tells AI how to work every session |
| `Phase1A_Adjustments_and_Fixes.md` | Fixes overlay | 📎 Manual (attach) | Corrections applied to all code |
| Plan PDFs / extracted text | Source of truth | 📎 Manual (attach) | WHAT to build each day/section |
| Your session prompt | Your writing | ✍️ You write it | Context + day + fixes for the agent |

---

## PART 6 — Missing Setup Items: Answers and Actions

### Item 1 — Run `@speckit.constitution` to fill `constitution.md`

**Status: REQUIRED before Day 1. This is a Day 0 task.**

`.specify/memory/constitution.md` currently has blank `[PLACEHOLDER]` tokens. Every SpecKit agent reads this file as its permanent project memory. Without filling it, `speckit.implement` generates code with no knowledge of your architectural rules.

**Steps:**
1. Open GitHub Copilot Chat in VS Code
2. Select `@speckit.constitution` from the agent dropdown
3. Paste the full content of `intsructions.txt` as your arguments
4. Attach `Phase1A_Adjustments_and_Fixes.md`
5. Say: "Fill the project constitution for the Event Ticketing Platform using these documents."
6. Agent writes filled content back to `.specify/memory/constitution.md`

**Verify:** Open `.specify/memory/constitution.md` — zero `[PLACEHOLDER]` tokens remain.

---

### Item 2 — Verify `create-new-feature.ps1` exists and is executable

**Status: ALREADY EXISTS. No action needed.**

All 5 SpecKit scripts are present in `.specify/scripts/powershell/`:
- `check-prerequisites.ps1` (4,805 bytes)
- `common.ps1` (9,670 bytes)
- `create-new-feature.ps1` (13,667 bytes)
- `setup-plan.ps1` (1,866 bytes)
- `update-agent-context.ps1` (23,280 bytes)

On Windows, scripts run without chmod. If you get an execution policy error:
```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

---

### Item 3 — Create `PROGRESS.md` at project root

**Status: DONE. Created at `Event-Ticketing-Platform/PROGRESS.md`**

Tracks day-by-day status and all 23 overlay fix statuses. Update at the end of every session.

---

### Item 4 — `MEMORY.md` — needed or auto-created?

**Status: NOT NEEDED. Do not create it.**

SpecKit's project memory IS `.specify/memory/constitution.md`. That is the only persistent memory file SpecKit agents read. There is no `MEMORY.md` concept in SpecKit.

What you DO maintain manually: `Docs/AI_Sessions/day-{N}-handoff.md` session handoffs. These are saved by you from the SESSION HANDOFF block printed at session end. They are NOT auto-created.

---

## PART 7 — Your Complete Daily Workflow (The Exact Protocol Every Day)

---

### PHASE A — Before Opening VS Code (2 minutes)

```
1. Open PROGRESS.md → confirm which day and what is pending
2. Open Docs/AI_Sessions/day-{N-1}-handoff.md → read "Next session start" line
3. Open Plans/session-prompts/day-{N}-*.md → read the entire day plan
4. Note today's fixes from the Fixes table in the session prompt
5. Know your TDD gate: what is the first test you will write today?
```

---

### PHASE B — Session Setup (3 minutes, in this exact order)

#### Step 1 — Paste `intsructions.txt` as your VERY FIRST MESSAGE

Open Copilot Chat. Before selecting any agent or attaching any file, paste the full
content of `intsructions.txt`. This sets the AI's behavior, rules, and architecture
decisions for the entire session. Raw paste — no subject line needed.

#### Step 2 — Attach Files (Maximum 3 slots)

| Session Type | Slot 1 (always) | Slot 2 (plan context) | Slot 3 (skill) |
|---|---|---|---|
| Backend implement | `Phase1A_Adjustments_and_Fixes.md` | Relevant Plan `.txt` | `java-springboot.SKILL.md` |
| DB + Migrations | `Phase1A_Adjustments_and_Fixes.md` | Relevant Plan `.txt` | `sql-optimization.SKILL.md` |
| N+1 / Query work | `Phase1A_Adjustments_and_Fixes.md` | Relevant Plan `.txt` | `postgresql-optimization.SKILL.md` |
| Frontend (Next.js) | Relevant Plan `.txt` | — | — (nextjs.instructions.md auto-injects) |
| Testing session | `Phase1A_Adjustments_and_Fixes.md` | Relevant Plan `.txt` | `java-junit.SKILL.md` |
| Docker / CI-CD | `Phase1A_Adjustments_and_Fixes.md` | Relevant Plan `.txt` | `multi-stage-dockerfile.SKILL.md` |

**Which Plan `.txt` file maps to which days:**

| Days | Plan File to Attach |
|------|---------------------|
| Days 1–7 | `Plans/Text/Phase1A_Section 2_ExecutionMap.txt` |
| Days 5–6 (Redis/RabbitMQ deep) | Add: `Plans/Text/Phase1A_Sections 6,7,8,9_ImplementationGuides.txt` |
| Days 8–10 | `Plans/Text/Phase1A_Sections 6,7,8,9_ImplementationGuides.txt` |
| Days 11–12 | `Plans/Text/Phase1A_Sections 10,11,12_EventMgmt_Pricing_Refund.txt` |
| Days 13–15 | `Plans/Text/Phase1A_Sections 3,4,5_Frontend_Calendar_Admin.txt` |
| Days 16–18 | `Plans/Text/Phase1A_Section 16_Testing_Deployment_And_devOps.txt` |
| Days 19–21 | `Plans/Text/Phase1A_Sections 13,14,15_16_Practices_Troubleshooting.txt` |

#### Step 3 — Select the SpecKit Agent from Dropdown

For most implementation days, use this chain:
```
speckit.tasks → speckit.implement
```
(Skip `speckit.analyze` and `speckit.plan` — the session prompts have already done that layer for you.)

For end-of-day or end-of-week validation:
```
speckit.checklist
```

#### Step 4 — Type Your First Prompt (Fill In the Template)

```
We are on Day {N} — {Theme}.
Feature folder: {feature-name} (e.g., booking-state-machine)

Active fixes for today (from Phase1A_Adjustments_and_Fixes.md):
- Fix {X.X} — CRITICAL: {one-line description}
- Fix {X.X} — IMPORTANT: {one-line description}
Cross-cutting (always active): Fix CC-1 (Correlation-ID), Fix CC-2 (BusinessConstants)

Pre-conditions from yesterday:
- ./mvnw test all green ✅
- {specific pre-condition from session prompt} ✅

TDD instruction — MANDATORY:
Write ALL test methods for today's service BEFORE any implementation code.
Run them and confirm they FAIL (red phase) before writing the service.
Tell me: what is the name of the first test class and first test method?

Non-negotiable rules — apply silently, do not ask:
- @RequiredArgsConstructor + private final (zero @Autowired)
- Instant everywhere (zero LocalDateTime)
- All constants via BusinessConstants (zero magic numbers)
- {day-specific rule from Anti-Pattern table, e.g., @EnableStateMachineFactory on Day 8}

Start with: the first test class. State its name and all test method signatures before coding.
```

---

### PHASE C — During the Session

Repeat this loop for each feature or task in the day:

```
STEP 1 — RED (Write Tests)
  Write the full test class with all test methods (bodies empty or throwing NotImplemented)
  Run: ./mvnw test -Dtest={TestClassName}
  ALL tests must FAIL. If any pass accidentally, the test is wrong.

STEP 2 — GREEN (Implement)
  Write the service/repository/controller
  Apply ALL overlay fixes inline — not as a separate step after
  Run: ./mvnw test -Dtest={TestClassName}
  ALL tests must pass.

STEP 3 — REFACTOR (Clean)
  No method > 20 lines → extract helpers
  No raw strings for states → use enums
  No magic numbers → use BusinessConstants
  Run tests again to confirm green still holds.

STEP 4 — COMPILE CHECK
  Run: ./mvnw compile
  Zero errors before moving to the next task.

STEP 5 — CONFIRM FIX APPLIED
  For each CRITICAL fix applied, state explicitly:
  "Fix {X.X} applied in {ClassName}.java at {method name}"
```

> **The Red phase is non-negotiable.** Tests written after implementation are verification tests,
> not design tests. They do not catch architecture problems. The Red phase does.

---

### PHASE D — End of Session (10 minutes)

#### Step 1 — Full test suite
```powershell
./mvnw test
# ALL tests must pass. Zero failures. Zero skips.
```

#### Step 2 — Git commit (conventional commit format)
```powershell
git add .
git commit -m "feat(day-{N}): {what was built}

Fixes applied: Fix {X.X}, Fix {X.X}
Tests added: {N} new tests, {M} total passing"
```

Commit message examples by day type:
- `feat(day-1): scaffold modular monolith with flyway migrations v1-v9`
- `feat(day-5): implement inventory service with lua atomic floor guard`
- `perf(day-6): eliminate n+1 queries with entity graph on event queries`
- `feat(day-8): implement booking state machine with toctou guard`
- `test(day-16): achieve 82 percent test coverage across all domains`

#### Step 3 — Save the SESSION HANDOFF

The AI prints a SESSION HANDOFF block at session end (defined in `intsructions.txt`).
Copy the entire block and save it:
```
Docs/AI_Sessions/day-{N}-handoff.md
```
If you skip this step, the next session starts with no knowledge of what was built.

#### Step 4 — Update `PROGRESS.md`
- Day row: ⬜ → ✅
- Each applied fix: ⬜ → ✅
- Metrics: update test count and coverage %

---

## PART 8 — TDD Alignment Audit (All 10 Session Prompts)

Result of verifying every prompt places tests BEFORE implementation (Red → Green → Refactor):

| Day | Status | Evidence |
|-----|--------|---------|
| Day 1 | ✅ No service logic | Entities + migrations only. No TDD layer needed. |
| Day 2 | ✅ Correct | "Step 3 — EventService Tests FIRST (Red → Green TDD)" before implementation |
| Day 3 | ✅ Correct | "3 tests each (Red first, then implement Green)" explicit in tasks |
| Day 4 | ✅ N/A | Frontend only. No Java tests. Coverage on Day 16. |
| Day 5 | ✅ Fixed | Tests now in STEP 1 (Red) before STEP 2 (implement). Concurrency test is gate. |
| Day 6 | ✅ Correct | Integration tests set up before N+1 fix is verified. Query count validates fix. |
| Day 7 | ✅ N/A | Cleanup day. All green test run is the gate — no new services. |
| Day 8 | ✅ Correct | "Write BookingStateMachineTest FIRST — 5 tests, all failing" is explicit. |
| Day 9 | ✅ Fixed | Tests written in Morning (Red) before Afternoon implementation (Green). |
| Day 10 | ⚠️ Review | Verify NotificationListenerTest is written before listener implementation. |

**All prompts for Days 1–9 are now correctly TDD-ordered.**

---

## PART 9 — The 3 Session Types

### Type A — Backend Implement (Days 1–3, 5, 7–12)
- Paste `intsructions.txt` → attach overlay + plan + skill → agent: `speckit.implement`
- TDD: Red → Green → Refactor mandatory for every service

### Type B — Frontend (Days 4, 13, 14, 15)
- Paste `intsructions.txt` → attach plan → standard Copilot (no SpecKit needed)
- `nextjs.instructions.md` auto-injects for `.tsx` files
- TDD: Not applicable for scaffold days; React component tests on Day 16

### Type C — Testing / Quality (Days 6, 16, 19, 20)
- Paste `intsructions.txt` → attach overlay + plan + `java-junit.SKILL.md`
- Agent: `speckit.checklist` for gap analysis, `speckit.implement` to fill missing tests
- Goal: reach and maintain 80%+ test coverage

---

## PART 10 — Quick-Reference Card

```
╔══════════════════════════════════════════════════════════════╗
║  EVERY SESSION — IN THIS ORDER                              ║
╠══════════════════════════════════════════════════════════════╣
║  1. Read PROGRESS.md + last day's handoff doc               ║
║  2. Read today's Plans/session-prompts/day-{N}-*.md         ║
║  3. Open Copilot Chat                                       ║
║  4. PASTE intsructions.txt as message 1                     ║
║  5. Attach: overlay + plan section + skill (max 3 files)    ║
║  6. Select speckit.tasks → speckit.implement from dropdown  ║
║  7. Type Day N prompt using the template in Part 7          ║
╠══════════════════════════════════════════════════════════════╣
║  DURING SESSION — THE TDD LOOP                              ║
╠══════════════════════════════════════════════════════════════╣
║  Write tests → run (ALL FAIL) → implement → run (ALL PASS)  ║
║  Apply overlay fixes inline → compile → confirm fix applied  ║
╠══════════════════════════════════════════════════════════════╣
║  END OF SESSION — ALL 4 STEPS                               ║
╠══════════════════════════════════════════════════════════════╣
║  ./mvnw test → ALL green                                    ║
║  git commit (conventional commit format)                    ║
║  Save handoff → Docs/AI_Sessions/day-{N}-handoff.md         ║
║  Update PROGRESS.md (day + fixes + metrics)                 ║
╠══════════════════════════════════════════════════════════════╣
║  NEVER DO THESE                                             ║
╠══════════════════════════════════════════════════════════════╣
║  Skip the Red phase (tests before code — always)            ║
║  Attach more than 3 files (context degrades past 3)         ║
║  Use @Autowired / LocalDateTime / magic numbers             ║
║  Use @EnableStateMachine (always @EnableStateMachineFactory) ║
║  Skip saving the session handoff (next day starts blind)     ║
╚══════════════════════════════════════════════════════════════╝
```
