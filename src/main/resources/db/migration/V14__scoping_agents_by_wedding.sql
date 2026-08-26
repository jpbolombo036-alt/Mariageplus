-- ============================================================
-- V14 : Scoping des agents par mariage
-- Additive et backward-compatible : wedding_id NULL = legacy org-wide.
-- Compatible PostgreSQL (production) ET H2 (tests), même style que V11/V12.
-- ============================================================

-- 1. Colonne wedding_id (nullable) + FK vers weddings.
ALTER TABLE organization_members ADD COLUMN IF NOT EXISTS wedding_id BIGINT;
ALTER TABLE organization_members
    ADD CONSTRAINT fk_org_members_wedding
    FOREIGN KEY (wedding_id) REFERENCES weddings(id);

-- 2. Remplacer l'ancienne contrainte unique org-wide (V1) par la contrainte
--    complète (user, org, role, wedding). Synchronisée avec l'entité.
ALTER TABLE organization_members DROP CONSTRAINT uk_org_members;
ALTER TABLE organization_members
    ADD CONSTRAINT uk_org_members UNIQUE (user_id, organization_id, role_id, wedding_id);

-- 3. L'index unique partiel org-wide (user, org, role) WHERE wedding_id IS NULL,
--    prévu pour bloquer les rôles org-wide en double, n'est PAS créé ici :
--    H2 (base des tests) ne supporte pas les index partiels. Le dédoublonnage
--    org-wide est donc assuré au niveau application (OrganizationMemberService).
--    Sur PostgreSQL uniquement, ajouter :
--    CREATE UNIQUE INDEX uk_org_members_org_wide
--      ON organization_members(user_id, organization_id, role_id)
--      WHERE deleted_at IS NULL AND wedding_id IS NULL;

-- 4. Index pour les recherches par mariage.
CREATE INDEX IF NOT EXISTS idx_org_members_wedding ON organization_members(wedding_id);