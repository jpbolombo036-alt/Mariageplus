-- ============================================================
-- V36 : Webhook Meta WhatsApp — suivi de livraison
--   notification_logs.message_id : wamid renvoyé par l'API à l'envoi,
--     clé de rattachement des statuts (delivered / read / failed).
--   invitations.delivered_at     : confirmation de livraison (webhook).
-- Idempotent : IF NOT EXISTS
-- ============================================================

ALTER TABLE notification_logs ADD COLUMN IF NOT EXISTS message_id VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_notif_logs_message ON notification_logs(message_id);

ALTER TABLE invitations ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;