-- V34 : les nouveaux refresh tokens ne stockent plus le JWT en clair.
-- La colonne historique doit donc accepter NULL.
ALTER TABLE refresh_tokens ALTER COLUMN token DROP NOT NULL;
