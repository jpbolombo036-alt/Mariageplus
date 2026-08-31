-- ============================================================
-- V20 : ajout du choix de boisson sur le RSVP
-- Additive, ne modifie pas V1..V19.
-- ============================================================

ALTER TABLE rsvps ADD COLUMN IF NOT EXISTS drink_choice VARCHAR(100);
