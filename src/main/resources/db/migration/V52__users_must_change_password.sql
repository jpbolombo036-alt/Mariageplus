-- V52 : changement de mot de passe obligatoire après une réinitialisation par le
-- SUPER_ADMIN (mot de passe temporaire transmis par WhatsApp). Tant que le drapeau
-- est posé, l'API n'accepte que le changement de mot de passe et la lecture du profil.
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
