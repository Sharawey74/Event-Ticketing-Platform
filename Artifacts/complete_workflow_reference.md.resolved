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
