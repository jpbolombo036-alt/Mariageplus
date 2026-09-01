-- ============================================================
-- V28 : carte d'invitation confirmée (PNG généré côté invité
-- après RSVP ACCEPTED, consultable par l'agent d'accueil).
-- Additive : ne modifie ni V1..V27 ni les données existantes.
--   card_key   : clé objet S3 (StorageService) — prioritaire
--   card_image : repli en base si le stockage S3 est désactivé
--                (même stratégie que avatars / photos d'événement)
-- ============================================================

ALTER TABLE invitations ADD COLUMN IF NOT EXISTS card_key VARCHAR(500);
ALTER TABLE invitations ADD COLUMN IF NOT EXISTS card_image BYTEA;
