-- ============================================================
-- V3 : Table weddings (module mariages) — additive, ne touche pas V1/V2
-- ============================================================

CREATE TABLE IF NOT EXISTS weddings (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    groom_first_name VARCHAR(100),
    groom_last_name VARCHAR(100),
    bride_first_name VARCHAR(100),
    bride_last_name VARCHAR(100),
    groom_photo_url VARCHAR(1000),
    bride_photo_url VARCHAR(1000),
    couple_photo_url VARCHAR(1000),
    description VARCHAR(2000),
    welcome_message VARCHAR(2000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_weddings_organization FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE INDEX IF NOT EXISTS idx_weddings_org ON weddings(organization_id);
CREATE INDEX IF NOT EXISTS idx_weddings_org_status ON weddings(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_weddings_status ON weddings(status);