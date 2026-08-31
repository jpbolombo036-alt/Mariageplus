-- Photos de la fiche mariage (marié, mariée, couple) :
--   *_photo_url  = clé objet S3 (ou URL externe) — colonnes existantes
--   *_image      = fallback base (BYTEA) si S3 non configuré
ALTER TABLE wedding_details ADD COLUMN IF NOT EXISTS groom_image BYTEA;
ALTER TABLE wedding_details ADD COLUMN IF NOT EXISTS bride_image BYTEA;
ALTER TABLE wedding_details ADD COLUMN IF NOT EXISTS couple_image BYTEA;
