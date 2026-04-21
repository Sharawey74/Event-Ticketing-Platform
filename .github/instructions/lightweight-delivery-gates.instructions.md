---
description: 'Lightweight delivery gates for Agent-mode workflow without SpecKit process overhead.'
applyTo: '**'
---

# Lightweight Delivery Gates

## Purpose

Use this workflow as the required quality gate for implementation and reviews.
This project does not require SpecKit phase artifacts as a blocker for coding.

## Required Gates (Must Pass)

1. **Red Gate**
   - For new behavior, write or update tests first.
   - Prove tests fail for the intended reason before implementation.

2. **Green Gate**
   - Implement the minimum production change to make failing tests pass.
   - Re-run the same targeted tests and confirm they pass.

3. **Compile Gate**
   - Run `./mvnw -q -DskipTests compile` after the change set.
   - Do not mark work complete if compile fails.

4. **Scope Gate**
   - Run relevant tests for touched modules.
   - For security-critical changes (auth, payment, webhook), include focused controller/service tests.

5. **Review Gate**
   - Perform a bug-first review: regressions, data integrity risks, auth risks, and concurrency risks.
   - If a risk is unresolved, do not close the task.

6. **Handoff Gate**
   - Update progress and session handoff with what was done, what remains, and exact next start point.

## Practical Rules

- Prefer targeted staging (`git add <file>`) over `git add .`.
- Keep controllers thin; business logic belongs in services.
- Follow project technical constraints from active Spring, security, and architecture instructions.
- If a gate cannot be run, state exactly why and record explicit follow-up action.
