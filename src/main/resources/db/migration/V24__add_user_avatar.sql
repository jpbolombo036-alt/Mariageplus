-- Avatar photo de profil (stocké en base, limité à 2 Mo côté application)
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar BYTEA;
