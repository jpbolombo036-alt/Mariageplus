-- ============================================================
-- V39 : Réglages par organisation avec héritage du global
--   whatsapp_enabled / event_creation_enabled : NULL = hériter du
--   réglage plateforme (app_settings), true/false = override.
-- Idempotent (IF NOT EXISTS).
-- ============================================================

CREATE TABLE IF NOT EXISTS organization_settings (
    organization_id BIGINT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    whatsapp_enabled BOOLEAN,
    event_creation_enabled BOOLEAN,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
