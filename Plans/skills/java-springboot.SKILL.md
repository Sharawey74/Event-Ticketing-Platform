---
name: java-springboot
description: 'Spring Boot best practices scoped to the VividPass Event Ticketing Platform'
---

# Spring Boot — Project-Specific Rules (VividPass)

This skill applies general Spring Boot best practices **plus the non-negotiable project rules** from `instructions.md` and `CLAUDE.md`. Every rule below is enforced — violations must be corrected before committing.

---

## ⛔ Non-Negotiable Anti-Patterns (project-absolute rules)

These are forbidden regardless of what any tutorial, Stack Overflow answer, or AI suggests:

| NEVER do this | ALWAYS do this instead | Why |
|---------------|------------------------|-----|
| `@Autowired` on fields | `@RequiredArgsConstructor` + `private final` | Testability, immutability |
| `LocalDateTime` for any timestamp | `java.time.Instant` | Timezone ambiguity in distributed systems |
| Two-step `SETNX + EXPIRE` | `setIfAbsent(key, value, Duration)` (single atomic call) | Race condition between the two steps |
| Two-step `INCR` then conditional `EXPIRE` | Lua script: INCR + EXPIRE in one atomic operation | Same race condition as above |
| Plain Redis decrement for inventory | Lua floor guard script | Allows negative inventory under concurrency |
| `@EnableStateMachine` | `@EnableStateMachineFactory` | Factory pattern required for per-booking instances |
| `existsByStripeEventId()` for webhook dedup | `UNIQUE(stripe_event_id)` + catch `DataIntegrityViolationException` | Database-level guarantee, not app-level race |
| Raw numeric literals in service/filter code | `BusinessConstants.FIELD_NAME` | Magic numbers make thresholds invisible and untraceable |
| `@Transactional` on `StripeWebhookController` | No `@Transactional` on webhook controller | Stripe processing must not be wrapped in a user-scope transaction |
| `@MockBean` in tests | `@MockitoBean` (Spring Boot 3.4+) | `@MockBean` is deprecated |
| `addFilters = false` in `@WebMvcTest` | `@Import(TestSecurityConfig.class)` | Disabling filters disables `@PreAuthorize` — security tests become worthless |
| `@Retryable` on `reserveTickets()` | `@Retryable` on `checkIn()` ONLY | `reserveTickets()` decrements Redis before DB save — retrying double-decrements inventory |
| `log.error("message: " + ex.getMessage())` | `log.error("message", ex)` | String concat swallows the stack trace |
| `log.warn("token={}", token)` in JWT code | `log.warn("jti={}", jti)` | Never log JWT token values — log only the `jti` claim |

---

## Dependency Injection

Always use Lombok's `@RequiredArgsConstructor` with `private final` fields. Never use `@Autowired`.

```java
// ✅ CORRECT
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {
    private final BookingRepository bookingRepository;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;
}

// ❌ WRONG
@Service
public class BookingService {
    @Autowired private BookingRepository bookingRepository;  // FORBIDDEN
}
```

---

## Timestamps — Always Instant

```java
// ✅ CORRECT
@Column(nullable = false)
private Instant createdAt = Instant.now();

// ❌ WRONG
private LocalDateTime createdAt;  // FORBIDDEN — timezone-unaware
```

Flyway migration column type: `TIMESTAMP WITH TIME ZONE` (PostgreSQL). Never `TIMESTAMP` without timezone.

---

## Transaction Management

- Class-level `@Transactional(readOnly = true)` on all service classes
- Override with `@Transactional` (no readOnly) on write methods only
- Never `@Transactional` on `StripeWebhookController`

```java
@Service
@Transactional(readOnly = true)  // default: all methods read-only
public class BookingService {

    @Transactional  // override: this method writes
    public BookingResponse reserveTickets(ReserveTicketsRequest request, Long userId) { ... }

    // No annotation needed — inherits readOnly = true from class
    public BookingDetailsResponse getBookingDetails(Long bookingId, Long userId) { ... }
}
```

---

## Redis Operations

### Distributed Lock (SET NX EX)

```java
// ✅ CORRECT — single atomic operation
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, userId.toString(), Duration.ofSeconds(BusinessConstants.LOCK_TTL_SECONDS));

// ❌ WRONG — two-step race condition
redisTemplate.opsForValue().setIfAbsent(lockKey, value);  // key set
redisTemplate.expire(lockKey, 30, TimeUnit.SECONDS);       // TTL set separately — race window here
```

### Inventory Lua Floor Guard

```java
// The Lua script executes atomically — NEVER replace with plain DECR
private static final RedisScript<Long> RESERVE_SCRIPT = RedisScript.of(
    "local current = redis.call('GET', KEYS[1]) " +
    "if current == false then return -2 end " +           // key missing
    "if tonumber(current) < tonumber(ARGV[1]) then return -1 end " +  // insufficient stock
    "return redis.call('DECRBY', KEYS[1], ARGV[1])",      // atomic decrement
    Long.class
);
// Return: >= 0 = success (remaining count), -1 = insufficient, -2 = key not found
```

### Rate Limit Counter (Atomic)

```java
// ✅ CORRECT — INCR + EXPIRE in single Lua execution
private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
    "local current = redis.call('INCR', KEYS[1]) " +
    "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
    "return current",
    Long.class
);

// ❌ WRONG — two-step race condition (same anti-pattern as SETNX + EXPIRE)
Long count = redisTemplate.opsForValue().increment(key);
if (count == 1) redisTemplate.expire(key, 60, TimeUnit.SECONDS);  // FORBIDDEN
```

---

## TOCTOU Guard — Check Inside Lock

When using a distributed lock for inventory, always re-check availability AFTER acquiring the lock:

```java
// ✅ CORRECT — double-check inside the lock
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, userId, Duration.ofSeconds(BusinessConstants.LOCK_TTL_SECONDS));
if (!Boolean.TRUE.equals(acquired)) throw new ConcurrentModificationException("Seat locked");

// Re-check INSIDE the lock (TOCTOU guard)
Long remaining = inventoryService.getAvailableCount(tierId);
if (remaining < quantity) throw new InsufficientInventoryException();

// Decrement via Lua
inventoryService.reserveSeat(tierId, quantity);
```

---

## Test Patterns

### Controller Tests (`@WebMvcTest`)

```java
@WebMvcTest(controllers = BookingController.class)
@Import(TestSecurityConfig.class)          // MANDATORY — never skip
class BookingControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean BookingService bookingService;  // @MockitoBean not @MockBean
    @MockitoBean JwtService jwtService;          // MANDATORY in @WebMvcTest

    // ❌ FORBIDDEN: @WebMvcTest(controllers=..., addFilters = false)
    // Disabling filters disables @PreAuthorize — all security tests become lies
}
```

Naming convention: `methodName_whenCondition_shouldExpectedBehavior`

```java
void reserveTickets_whenInventoryExhausted_shouldReturn409()
void checkIn_whenNotOrganizer_shouldReturn403()
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
class BookingServiceIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);
    // @Container static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    // H2 is FORBIDDEN — always use real containers
}
```

### Concurrency Tests — NEVER `@Transactional` on Class

```java
@SpringBootTest
@Testcontainers
// ❌ NEVER: @Transactional on a concurrency test class
// Threads spawned by ExecutorService do NOT share the test's transaction context
class InventoryServiceConcurrencyTest {
    // Use @BeforeEach / @AfterEach for setup/teardown — NOT @Transactional
}
```

### JaCoCo Coverage Gate

The project enforces **80% INSTRUCTION coverage** (not LINE). This runs during `./mvnw verify`:

```xml
<counter>INSTRUCTION</counter>  <!-- not LINE — INSTRUCTION is stricter -->
<value>COVEREDRATIO</value>
<minimum>0.80</minimum>
```

Exclusions (only these): `dto/**`, `model/**`, `config/**`, `exception/**`, `*Application.class`  
Never exclude: services, controllers, filters, handlers, or any class containing business logic.

---

## BusinessConstants — No Magic Numbers

Every threshold, TTL, limit, and constant must be in `BusinessConstants.java`:

```java
public final class BusinessConstants {
    public static final int RESERVATION_TTL_SECONDS           = 900;   // 15 min
    public static final int LOCK_TTL_SECONDS                  = 30;
    public static final int RATE_LIMIT_AUTH_REQUESTS_PER_MIN  = 10;
    public static final int RATE_LIMIT_BOOKING_REQUESTS_PER_MIN = 5;
    public static final int MAX_TICKETS_PER_BOOKING           = 10;
}
```

---

## Global Exception Handler — Mandatory Catch-All

`GlobalExceptionHandler` MUST have an `Exception.class` handler as the last entry:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, WebRequest request) {
    String correlationId = MDC.get("correlationId");
    log.error("Unhandled exception — correlationId={}", correlationId, ex);  // ex as LAST arg
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.failure("An unexpected error occurred. Reference: " + correlationId));
}
```

---

## Spring Retry

`@Retryable` is permitted ONLY on `BookingService.checkIn()`:

```java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
@Transactional
public BookingResponse checkIn(Long bookingId, Long organizerId) { ... }

// ❌ FORBIDDEN on:
// reserveTickets() — Redis decrement before DB save makes retry = double decrement
// Any inventory operation — always has Redis side effects
```

---

## Flyway — Immutable Migrations

- V1–V11 are APPLIED and IMMUTABLE. Never edit them.
- New schema changes → create `V12__description.sql`, `V13__description.sql`, etc.
- Migration column addition requires `DEFAULT` for existing rows.
- New NOT NULL columns require a two-step migration: add nullable → backfill → add constraint.

---

## Logging Best Practices

```java
// ✅ CORRECT — exception always last arg (preserves stack trace)
log.error("Failed to process booking bookingId={}", bookingId, ex);
log.error("Unhandled exception", ex);

// ❌ WRONG — stack trace silently lost
log.error("Failed: " + ex.getMessage());  // string concat — FORBIDDEN
log.error("Failed to process booking");   // no exception arg — FORBIDDEN

// ✅ CORRECT — correlation ID in every log entry
log.info("Booking created — bookingId={} correlationId={}", bookingId, MDC.get("correlationId"));
```
