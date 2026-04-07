# Plans/skills — Quick Reference

This directory contains copies of the Awesome Copilot SKILL.md files that are relevant to the Event Ticketing Platform.
Original source: `c:\Users\DELL\Desktop\awesome-copilot\skills\`

## How to Use Skills

Skills are manually attached in GitHub Copilot Chat. You **drag-and-drop** the SKILL.md file into the chat context,
or use `#file:` references. Unlike instructions (which are auto-injected), skills require manual attachment.

## Skills in This Directory

| File | Attach When | What It Does |
|------|-------------|--------------|
| `java-springboot.SKILL.md` | **Every backend implement session** | Constructor injection, @Transactional, Spring Data JPA patterns, testing |
| `java-junit.SKILL.md` | Day 16+ test coverage push | JUnit 5, parameterized tests, AssertJ, Mockito, TestContainers patterns |
| `postgresql-optimization.SKILL.md` | Day 6 (N+1 fixes), Day 16 query analysis | EXPLAIN ANALYZE, index strategies, JSONB, window functions |
| `sql-optimization.SKILL.md` | Days 1, 5, 6 (Flyway migrations) | Query tuning, index design, pagination, batch operations |
| `multi-stage-dockerfile.SKILL.md` | Day 3, Day 7 (Docker Compose polish) | Multi-stage Dockerfiles, layer caching, security practices |
| `conventional-commit.SKILL.md` | Every commit | Conventional commit messages, scope, breaking changes |

## Attachment Rules

- **Max 3 attachments per session** before context degrades
- Standard session: `java-springboot.SKILL.md` + `Phase1A_Adjustments_and_Fixes.md` + relevant plan section = 3 files
- Do NOT attach postgresql-optimization AND sql-optimization in the same session (too similar, wastes context)

## Original Source Locations
```
c:\Users\DELL\Desktop\awesome-copilot\skills\java-springboot\SKILL.md
c:\Users\DELL\Desktop\awesome-copilot\skills\java-junit\SKILL.md
c:\Users\DELL\Desktop\awesome-copilot\skills\postgresql-optimization\SKILL.md
c:\Users\DELL\Desktop\awesome-copilot\skills\sql-optimization\SKILL.md
c:\Users\DELL\Desktop\awesome-copilot\skills\multi-stage-dockerfile\SKILL.md
c:\Users\DELL\Desktop\awesome-copilot\skills\conventional-commit\SKILL.md
```
