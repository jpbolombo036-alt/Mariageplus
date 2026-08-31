-- ============================================================
-- V21 : table des boissons par événement
-- ============================================================

CREATE TABLE IF NOT EXISTS drinks (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL REFERENCES events(id),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drinks_wedding ON drinks(wedding_id);
