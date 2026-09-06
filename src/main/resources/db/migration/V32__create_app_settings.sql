-- ============================================================
-- V32 : Réglages plateforme (interrupteurs globaux SUPER_ADMIN)
--   app_settings : clé/valeur, une ligne par réglage.
--   whatsapp_sending_enabled : coupe les envois WhatsApp de toute
--   la plateforme (boutons désactivés côté front + API refusée).
-- Idempotent : IF NOT EXISTS / WHERE NOT EXISTS
-- ============================================================
CREATE TABLE IF NOT EXISTS app_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(100)  NOT NULL UNIQUE,
    setting_value VARCHAR(255),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

INSERT INTO app_settings (setting_key, setting_value, created_at, updated_at)
SELECT 'whatsapp_sending_enabled', 'true', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp_sending_enabled');