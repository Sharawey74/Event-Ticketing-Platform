CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    booking_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    state VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    total_amount DECIMAL(10,2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ,
    stripe_session_id VARCHAR(255),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    tier_id BIGINT NOT NULL REFERENCES ticket_tiers(id),
    seat_number VARCHAR(20),
    qr_code TEXT,
    check_in_status BOOLEAN NOT NULL DEFAULT FALSE,
    check_in_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
