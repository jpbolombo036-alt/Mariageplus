-- ============================================================
-- V11 : refresh tokens + token version pour révocation JWT
-- Additive, ne modifie pas V1..V10.
-- Compatible PostgreSQL (production) et H2 (tests).
-- ============================================================

-- ---------- Version de token par utilisateur ----------
-- Permet d'invalider immédiatement tous les JWT émis lors d'un
-- changement de rôle/permission, d'une désactivation ou d'un logout.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

-- ---------- Refresh tokens ----------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    organization_id BIGINT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
