-- ============================================================
-- V15 : Invitations enrichies — contenu email, relances, suivi
-- Additives. Compatible PostgreSQL (prod) ET H2 (tests).
-- ============================================================

-- Message personnalisé de l'organisateur (affiché dans l'email et la page invité).
ALTER TABLE weddings ADD COLUMN IF NOT EXISTS message VARCHAR(2000);

-- Compteur de relances + horodatage de première ouverture du lien public (suivi).
ALTER TABLE invitations ADD COLUMN IF NOT EXISTS reminder_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE invitations ADD COLUMN IF NOT EXISTS opened_at TIMESTAMP;