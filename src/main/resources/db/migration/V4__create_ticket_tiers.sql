CREATE TABLE ticket_tiers (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    tier_name VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    base_price DECIMAL(10,2) NOT NULL,
    total_capacity INT NOT NULL,
    available_count INT NOT NULL,
    max_per_booking INT NOT NULL DEFAULT 10,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_ticket_tiers_event_tier UNIQUE (event_id, tier_name)
);
