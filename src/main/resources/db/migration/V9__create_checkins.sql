-- ============================================================
-- V9 : table checkins (module QR Code + Check-in)
-- Additive, ne modifie pas V1..V8.
-- Chaque ligne = une opération réelle de contrôle (arrivées
-- partielles autorisées). Le total entré = SUM(number_of_attendees).
-- Compatible PostgreSQL (production) et H2 (tests).
-- ============================================================

CREATE TABLE IF NOT EXISTS checkins (
    id BIGSERIAL PRIMARY KEY,
    invitation_id BIGINT NOT NULL,
    number_of_attendees INTEGER NOT NULL,
    checked_in_at TIMESTAMP NOT NULL,
    checked_in_by BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_checkins_invitation FOREIGN KEY (invitation_id) REFERENCES invitations(id)
);

CREATE INDEX IF NOT EXISTS idx_checkins_invitation ON checkins(invitation_id);
CREATE INDEX IF NOT EXISTS idx_checkins_checked_at ON checkins(checked_in_at);