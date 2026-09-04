-- ============================================================
-- V30 : Envoi en masse (WhatsApp) — batches + journal de notifications
--   bulk_send_batches  : un "envoyer en masse" déclenché par l'organisateur
--   notification_logs  : résultat par invitation (SENT / FAILED / SKIPPED)
-- Idempotent : IF NOT EXISTS
-- ============================================================

CREATE TABLE IF NOT EXISTS bulk_send_batches (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL,
    organization_id BIGINT,
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INT NOT NULL DEFAULT 0,
    sent_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bulk_batches_wedding ON bulk_send_batches(wedding_id);

CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT,
    wedding_id BIGINT NOT NULL,
    invitation_id BIGINT NOT NULL,
    guest_id BIGINT,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notif_logs_batch ON notification_logs(batch_id);
CREATE INDEX IF NOT EXISTS idx_notif_logs_invitation ON notification_logs(invitation_id);
