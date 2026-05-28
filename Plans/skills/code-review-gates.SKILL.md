---
name: code-review-gates
description: Perform bug-first code review with severity ordering and test gap detection.
---

# Code Review Gates

## Attach This Skill When

- Reviewing a completed change set before merge.
- Auditing a day handoff for hidden regressions.

## Review Method

1. Prioritize findings by severity: Critical, High, Medium, Low.
2. Focus on bugs and regressions first, not style.
3. Validate behavior against expected acceptance outcome.
4. Identify missing tests that allow regressions.

## Must-Check Areas

- Business logic moved into controller by mistake
- Broken authorization assumptions
- Incorrect time handling (must be UTC `Instant`)
- Wrong transaction boundaries
- DTO/entity boundary leaks
- Missing validation on input paths

## Required Output

1. Findings with file and line references
2. Open assumptions/questions
3. Minimal fix plan
