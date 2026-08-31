-- Avatar photo de profil : clé objet dans le stockage S3-compatible
-- (l'ancienne colonne BYTEA `avatar` reste en fallback si S3 n'est pas configuré)
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
