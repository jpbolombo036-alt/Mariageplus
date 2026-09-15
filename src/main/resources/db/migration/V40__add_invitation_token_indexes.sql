-- ============================================================
-- V40 : Index de performance pour le chemin public à fort trafic
--   invitations.public_token  → chaque visite de lien, RSVP et check-in
--   invitations.invitation_code → résolution par code
-- Sans index : full scan de la table à chaque visite (rgression à 10k+).
-- Index simples (non uniques) : compatibles H2 (tests) et sans risque de
-- échec au démarrage si d'éventuels doublons historiques existent.
-- Idempotent (IF NOT EXISTS).
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_invitations_public_token ON invitations(public_token);
CREATE INDEX IF NOT EXISTS idx_invitations_code ON invitations(invitation_code);
