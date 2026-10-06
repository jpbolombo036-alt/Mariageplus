-- Idempotence de la synchronisation hors ligne : une paire (device_id,
-- sequence_number) ne peut être insérée qu'une seule fois. Un rejeu de batch
-- (perte de la réponse réseau) est donc impossible à doubler en base.
-- Les check-ins en ligne (device_id NULL) ne sont pas concernés : les valeurs
-- NULL restent distinctes dans une index unique (PostgreSQL et H2).
ALTER TABLE checkins ADD COLUMN IF NOT EXISTS sequence_number BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_checkins_device_sequence
    ON checkins (device_id, sequence_number);