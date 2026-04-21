---
name: implementation-gate-review
description: Review implementation changes with strict Red/Green/Compile/Scope gates before completion.
---

# Implementation Gate Review

## Attach This Skill When

- You finish a feature block and need a final implementation quality check.
- You want a quick pass/fail decision before commit.

## Review Checklist

1. **Red Evidence**
   - Was there a failing test for the intended behavior before code change?

2. **Green Evidence**
   - Do targeted tests now pass?

3. **Compile Evidence**
   - Does `./mvnw -q -DskipTests compile` pass?

4. **Scope Evidence**
   - Were relevant module tests run for touched components?

5. **Regression Sweep**
   - Any behavior change outside intended scope?
   - Any DTO/API contract drift?

6. **Decision**
   - PASS if all gates pass.
   - BLOCK if any gate fails or evidence is missing.

## Expected Output Format

- Gate status table (PASS/BLOCK per gate)
- Top risks (max 5)
- Required fixes before merge
