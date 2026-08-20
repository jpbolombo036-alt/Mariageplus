-- V12 : protection contre les tentatives de connexion répétées.
-- Les valeurs par défaut préservent le comportement des comptes existants.
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
