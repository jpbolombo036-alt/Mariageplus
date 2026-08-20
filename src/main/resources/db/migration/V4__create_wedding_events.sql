-- ============================================================
-- V4 : Table wedding_events (module événements) — additive, ne touche pas V1..V3
-- ============================================================

CREATE TABLE IF NOT EXISTS wedding_events (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    description VARCHAR(1000),
    event_date DATE,
    start_time TIME,
    end_time TIME,
    venue_name VARCHAR(200),
    venue_address VARCHAR(255),
    city VARCHAR(100),
    commune VARCHAR(100),
    country VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    map_url VARCHAR(1000),
    display_order INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_wedding_events_wedding FOREIGN KEY (wedding_id) REFERENCES weddings(id)
);

CREATE INDEX IF NOT EXISTS idx_wedding_events_wedding ON wedding_events(wedding_id);
CREATE INDEX IF NOT EXISTS idx_wedding_events_wedding_date ON wedding_events(wedding_id, event_date);