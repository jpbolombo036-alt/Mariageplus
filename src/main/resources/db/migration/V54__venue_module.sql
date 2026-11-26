-- ============================================================
-- V54 : module « Salles » (gestion de salle de fête)
--
-- Activé organisation par organisation par le SUPER_ADMIN
-- (organization_settings.venue_module_enabled). Les montants sont en
-- NUMERIC(14,2) ; une réservation a une devise (USD ou CDF) et chaque
-- paiement garde sa propre devise et le taux appliqué (CDF pour 1 USD).
--
-- Double réservation : empêchée côté application sous verrou pessimiste
-- de la ligne venues (SELECT ... FOR UPDATE), compatible PostgreSQL et H2.
-- ============================================================

ALTER TABLE organization_settings ADD COLUMN IF NOT EXISTS venue_module_enabled BOOLEAN;

CREATE TABLE IF NOT EXISTS venues (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    address VARCHAR(255),
    commune VARCHAR(100),
    city VARCHAR(100),
    country VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    capacity_seated INTEGER,
    capacity_standing INTEGER,
    amenities VARCHAR(1000),
    image_key VARCHAR(500),
    image BYTEA,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_venues_org ON venues(organization_id);

CREATE TABLE IF NOT EXISTS venue_rates (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    label VARCHAR(100) NOT NULL,
    price NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT ck_venue_rates_price CHECK (price >= 0)
);
CREATE INDEX IF NOT EXISTS idx_venue_rates_venue ON venue_rates(venue_id);

CREATE TABLE IF NOT EXISTS venue_clients (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    first_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(255),
    notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_venue_clients_org ON venue_clients(organization_id);
CREATE INDEX IF NOT EXISTS idx_venue_clients_phone ON venue_clients(phone);

CREATE TABLE IF NOT EXISTS venue_bookings (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    client_id BIGINT NOT NULL REFERENCES venue_clients(id),
    title VARCHAR(150),
    event_type VARCHAR(30),
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    guest_count INTEGER,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rate_id BIGINT REFERENCES venue_rates(id),
    base_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    option_expires_at TIMESTAMP,
    notes VARCHAR(2000),
    cancel_reason VARCHAR(500),
    event_id BIGINT REFERENCES events(id),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT ck_venue_bookings_period CHECK (end_at > start_at),
    CONSTRAINT ck_venue_bookings_amounts CHECK (base_price >= 0 AND discount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_venue_bookings_org ON venue_bookings(organization_id);
CREATE INDEX IF NOT EXISTS idx_venue_bookings_venue_period ON venue_bookings(venue_id, start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_venue_bookings_client ON venue_bookings(client_id);
CREATE INDEX IF NOT EXISTS idx_venue_bookings_event ON venue_bookings(event_id);

CREATE TABLE IF NOT EXISTS venue_booking_extras (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES venue_bookings(id),
    label VARCHAR(150) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT ck_venue_booking_extras CHECK (quantity > 0 AND unit_price >= 0)
);
CREATE INDEX IF NOT EXISTS idx_venue_booking_extras_booking ON venue_booking_extras(booking_id);

CREATE TABLE IF NOT EXISTS venue_booking_payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES venue_bookings(id),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(14,4),
    amount_in_booking_currency NUMERIC(14,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    reference VARCHAR(100),
    paid_at TIMESTAMP NOT NULL,
    note VARCHAR(500),
    recorded_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT ck_venue_booking_payments_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_venue_booking_payments_booking ON venue_booking_payments(booking_id);
CREATE INDEX IF NOT EXISTS idx_venue_booking_payments_paid_at ON venue_booking_payments(paid_at);

-- Permissions du module : ORGANISATEUR et SUPER_ADMIN.
INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT v.code, v.libelle, v.categorie, NOW(), NOW() FROM (
    SELECT 'VENUE_VIEW' AS code, 'Voir les salles' AS libelle, 'VENUES' AS categorie
    UNION ALL SELECT 'VENUE_MANAGE', 'Gérer les salles et leurs tarifs', 'VENUES'
    UNION ALL SELECT 'VENUE_CLIENT_MANAGE', 'Gérer les clients de salle', 'VENUES'
    UNION ALL SELECT 'BOOKING_VIEW', 'Voir les réservations de salle', 'VENUES'
    UNION ALL SELECT 'BOOKING_MANAGE', 'Gérer les réservations de salle', 'VENUES'
    UNION ALL SELECT 'BOOKING_PAYMENT_RECORD', 'Enregistrer les paiements de réservation', 'VENUES'
    UNION ALL SELECT 'VENUE_REPORT_VIEW', 'Voir les rapports financiers des salles', 'VENUES'
) AS v
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ORGANISATEUR', 'SUPER_ADMIN')
  AND p.code IN ('VENUE_VIEW', 'VENUE_MANAGE', 'VENUE_CLIENT_MANAGE', 'BOOKING_VIEW',
                 'BOOKING_MANAGE', 'BOOKING_PAYMENT_RECORD', 'VENUE_REPORT_VIEW')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
