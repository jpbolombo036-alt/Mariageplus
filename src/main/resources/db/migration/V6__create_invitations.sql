-- ============================================================
-- V6 : Table invitations + permissions (INVITATION_* déjà en V2)
-- Additive, ne touche pas V1..V5.
-- ============================================================

CREATE TABLE IF NOT EXISTS invitations (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL,
    guest_id BIGINT NOT NULL,
    invitation_code VARCHAR(30) NOT NULL,
    public_token VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    sent_at TIMESTAMP,
    last_sent_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_invitations_code UNIQUE (invitation_code),
    CONSTRAINT uk_invitations_public_token UNIQUE (public_token),
    CONSTRAINT fk_invitations_wedding FOREIGN KEY (wedding_id) REFERENCES weddings(id),
    CONSTRAINT fk_invitations_guest FOREIGN KEY (guest_id) REFERENCES guests(id)
);

CREATE INDEX IF NOT EXISTS idx_invitations_wedding ON invitations(wedding_id);
CREATE INDEX IF NOT EXISTS idx_invitations_guest ON invitations(guest_id);

-- NB : pas de contrainte UNIQUE sur guest_id : la règle "une invitation active
-- par invité" est appliquée côté service (existsByGuestId), ce qui permet de
-- recréer une invitation après soft-delete de l'ancienne.