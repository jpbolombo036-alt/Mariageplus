-- ============================================================
-- V38 : Catalogue global de boissons + disponibilité par événement
--   drink_catalog : boissons globales (créées par le SUPER_ADMIN)
--   event_drinks  : pivot événement ↔ boisson de catalogue — l'organisateur
--                   déclare les boissons disponibles pour ses invités
-- Tables manquantes en production (ddl-auto=validate) : l'app ne démarrait
-- plus (Schema-validation: missing table [drink_catalog]). Idempotent.
-- ============================================================

CREATE TABLE IF NOT EXISTS drink_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER,
    image_key VARCHAR(500),
    image BYTEA,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drink_catalog_name ON drink_catalog(name);

CREATE TABLE IF NOT EXISTS event_drinks (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL REFERENCES events(id),
    catalog_item_id BIGINT NOT NULL REFERENCES drink_catalog(id),
    available BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_event_drinks_wedding ON event_drinks(wedding_id);
CREATE INDEX IF NOT EXISTS idx_event_drinks_catalog ON event_drinks(catalog_item_id);
