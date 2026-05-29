-- V11__add_waitlist_and_refund_reason.sql
-- Day 11: Waitlist Entries table + refund_denial_reason field (Fix 12.1 — planned ahead)
--
-- IMMUTABLE: Do not edit after first run.

-- ─────────────────────────────────────────────────────────────────────────────
-- Waitlist Entries
-- Users join this table when a ticket tier is sold out.
-- Entries are ordered by created_at ASC (first-come first-served).
-- UNIQUE(user_id, tier_id) ensures one entry per user per tier.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE waitlist_entries (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    tier_id     BIGINT      NOT NULL REFERENCES ticket_tiers(id)  ON DELETE CASCADE,
    user_email  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_waitlist_user_tier UNIQUE (user_id, tier_id)
);

CREATE INDEX idx_waitlist_tier_created ON waitlist_entries (tier_id, created_at ASC);

-- ─────────────────────────────────────────────────────────────────────────────
-- Fix 12.1 (planned ahead) — refund_denial_reason
-- When a refund is denied (< 3 days before event), the reason is stored here
-- so the user can see it on their booking page and in their notification email.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS refund_denial_reason VARCHAR(500) NULL;
