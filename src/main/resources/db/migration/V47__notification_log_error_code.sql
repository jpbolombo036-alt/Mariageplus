-- V44 : Ajout de la colonne error_code sur notification_logs pour le diagnostic
-- des échecs WhatsApp (codes d'erreur Meta catégorisés).
ALTER TABLE notification_logs ADD COLUMN error_code INT DEFAULT 0;
CREATE INDEX idx_notif_logs_error_code ON notification_logs (error_code);