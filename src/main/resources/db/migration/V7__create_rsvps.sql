-- ============================================================
-- V7 : Table rsvps (module RSVP public) — additive, ne touche pas V1..V6
-- ============================================================

CREATE TABLE IF NOT EXISTS rsvps (
    id BIGSERIAL PRIMARY KEY,
    invitation_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    responded_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_rsvps_invitation UNIQUE (invitation_id),
    CONSTRAINT fk_rsvps_invitation FOREIGN KEY (invitation_id) REFERENCES invitations(id)
);
