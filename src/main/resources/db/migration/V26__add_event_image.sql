-- Photo de couverture des événements :
--   image_key = clé objet dans le stockage S3-compatible (prioritaire)
--   image     = fallback base (BYTEA) si S3 non configuré
ALTER TABLE events ADD COLUMN IF NOT EXISTS image_key VARCHAR(500);
ALTER TABLE events ADD COLUMN IF NOT EXISTS image BYTEA;
