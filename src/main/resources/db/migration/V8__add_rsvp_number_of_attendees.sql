-- ============================================================
-- V8 : ajout du nombre de participants attendus sur le RSVP
-- Additive, ne modifie pas V1..V7.
-- maximumAllowed (= 1 + guest.allowedCompanions) est calculé
-- côté backend au moment de la soumission, jamais fourni par le client.
-- ============================================================

ALTER TABLE rsvps ADD COLUMN number_of_attendees INTEGER;