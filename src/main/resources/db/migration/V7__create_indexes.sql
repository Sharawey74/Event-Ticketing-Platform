CREATE INDEX idx_events_start_date ON events(start_date);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_organizer_id ON events(organizer_id);

CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_event_id ON bookings(event_id);
CREATE INDEX idx_bookings_state ON bookings(state);
CREATE INDEX idx_bookings_expires_at_reserved ON bookings(expires_at) WHERE state = 'RESERVED';

CREATE INDEX idx_venues_city ON venues(city);
