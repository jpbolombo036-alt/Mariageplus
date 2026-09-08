-- V33 : ne plus stocker les refresh tokens en clair.
-- Les lignes historiques restent lisibles temporairement via la colonne token
-- afin de permettre une migration progressive lors de leur prochain refresh.
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);
