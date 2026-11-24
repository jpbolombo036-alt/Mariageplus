-- V51 : notifications in-app des utilisateurs (cloche des organisateurs).
-- Alimentées par les messages du SUPER_ADMIN, envoyés à une organisation ou à
-- tous les organisateurs. Une ligne par destinataire : lecture propre à chacun.
CREATE TABLE IF NOT EXISTS user_notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000),
    sender_user_id BIGINT,
    organization_id BIGINT,
    broadcast BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_notif_user_created ON user_notifications (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_notif_user_unread ON user_notifications (user_id, read_at);
