-- V50 : notifications in-app pour le SUPER_ADMIN (cloche de l'en-tête).
-- Alimentées par les inscriptions d'organisations et les messages envoyés par
-- les organisateurs en attente d'activation. Indépendantes du SMTP.
CREATE TABLE IF NOT EXISTS admin_notifications (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000),
    organization_id BIGINT,
    user_id BIGINT,
    sender_email VARCHAR(255),
    read_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_notif_created ON admin_notifications (created_at);
CREATE INDEX IF NOT EXISTS idx_admin_notif_unread ON admin_notifications (read_at);
CREATE INDEX IF NOT EXISTS idx_admin_notif_sender ON admin_notifications (type, user_id, created_at);
