# Event Ticketing Platform — Claude Code Context

## Read these files first (always, before any code)

@AI_CONTEXT.md      ← full project state, architecture, fix status
@instructions.md    ← master rules, anti-patterns, TDD mandate
@PROGRESS.md        ← current day status, overlay fix tracker

---

## Current state (Day 16)

- Branch: day-15-frontend-organizer-dashboard
- Tests: 99/99 passing
- Next: Day 16 — Backend Test Coverage Push (80%+)
- Next migration: V12__...

## Critical rules (never break these)

- @RequiredArgsConstructor + private final — NEVER @Autowired
- java.time.Instant — NEVER LocalDateTime
- TDD: write failing test FIRST, then implement
- setIfAbsent(key, value, Duration) — NEVER two-step SETNX + EXPIRE
- Lua floor guard — NEVER plain decrement for inventory
- @EnableStateMachineFactory — NEVER @EnableStateMachine
- UNIQUE constraint + DataIntegrityViolationException — NOT existsByStripeEventId()
- BusinessConstants.X — NEVER raw magic numbers
- Check availability INSIDE the lock (TOCTOU guard)
- StripeWebhookController must NOT be @Transactional
- Flyway migrations are IMMUTABLE — create V12+ for changes

## Test pattern (mandatory)

- @WebMvcTest → @Import(TestSecurityConfig.class) + @MockitoBean JwtService
- Integration: @SpringBootTest + @Testcontainers (postgres:17, redis:7, rabbitmq:4)
- Naming: methodName_whenCondition_shouldExpectedBehavior

## Pending fix

- Fix 16.1 (CRITICAL): concurrency test for reserveSeat() Lua script
