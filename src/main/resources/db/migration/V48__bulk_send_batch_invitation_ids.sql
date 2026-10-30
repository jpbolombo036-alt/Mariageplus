-- V48 : Stocker les IDs des invitations ciblées sur le batch pour permettre
-- le réessai automatique des batchs PENDING coincés (ex : file d'attente pleine).
ALTER TABLE bulk_send_batches ADD COLUMN invitation_ids TEXT;
ALTER TABLE bulk_send_batches ADD COLUMN reminder_limit INT DEFAULT 0;