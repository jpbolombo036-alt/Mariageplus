-- ============================================================
-- V59 : module « Salles » — dépenses
--
-- Une dépense est une sortie d'argent de la salle (électricité, carburant du
-- groupe électrogène, personnel, entretien…), en USD ou en CDF. Elle peut
-- concerner une salle précise ou toute l'organisation (venue_id NULL).
-- Une dépense payée par un caissier diminue « Ma caisse » ; le tableau de bord
-- montre encaissé − dépensé. Annuler une dépense = suppression douce (gérant).
--
-- Permissions :
--   EXPENSE_RECORD : saisir une dépense (gérant, caissier, SUPER_ADMIN)
--   EXPENSE_MANAGE : voir toutes les dépenses, modifier, annuler (gérant, SUPER_ADMIN)
-- ============================================================

CREATE TABLE IF NOT EXISTS venue_expenses (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    venue_id BIGINT REFERENCES venues(id),
    category VARCHAR(30) NOT NULL,
    label VARCHAR(200) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    method VARCHAR(20) NOT NULL,
    reference VARCHAR(100),
    spent_at TIMESTAMP NOT NULL,
    note VARCHAR(500),
    recorded_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT ck_venue_expenses_positive CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_venue_expenses_org_spent ON venue_expenses(organization_id, spent_at);
CREATE INDEX IF NOT EXISTS idx_venue_expenses_recorder ON venue_expenses(recorded_by, spent_at);

INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT 'EXPENSE_RECORD', 'Saisir une dépense de salle', 'VENUES', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'EXPENSE_RECORD');

INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT 'EXPENSE_MANAGE', 'Voir toutes les dépenses de salle, les modifier et les annuler', 'VENUES', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'EXPENSE_MANAGE');

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ORGANISATEUR', 'SUPER_ADMIN') AND p.code IN ('EXPENSE_RECORD', 'EXPENSE_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AGENT_SALLE' AND p.code = 'EXPENSE_RECORD'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
