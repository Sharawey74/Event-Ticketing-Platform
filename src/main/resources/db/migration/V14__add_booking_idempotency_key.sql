-- Fix 26-idem: make the Idempotency-Key header mean something.
--
-- Until now RateLimitFilter only checked the header was PRESENT (RateLimitFilter.java:83) and
-- never stored or compared the value, so a client timeout on a request that had actually
-- succeeded, a second browser tab, or an automatic network retry could each create a second
-- booking — and a second Stripe charge. That is the failure that charged booking 562 twice.
--
-- The guard is the same one Fix 9.2 already uses for Stripe webhooks: a UNIQUE constraint,
-- with the application catching DataIntegrityViolationException. An application-level
-- "have I seen this key?" check cannot work — two concurrent requests both pass it.

ALTER TABLE bookings ADD COLUMN idempotency_key VARCHAR(255);

-- PostgreSQL treats NULLs as distinct under a UNIQUE constraint, so every pre-existing booking
-- and every future request that omits the header coexist freely. Only real keys are constrained.
ALTER TABLE bookings
    ADD CONSTRAINT uq_bookings_idempotency_key UNIQUE (idempotency_key);

COMMENT ON COLUMN bookings.idempotency_key IS
    'Client-supplied Idempotency-Key for POST /api/v1/bookings. NULL for bookings created without one.';
