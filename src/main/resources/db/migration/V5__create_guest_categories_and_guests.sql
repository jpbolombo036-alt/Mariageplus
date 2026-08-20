-- ============================================================
-- V5 : guest_categories + guests + permissions CATEGORY_*
-- Additive, ne touche pas V1..V4.
-- ============================================================

-- ---------- Catégories d'invités ----------
CREATE TABLE IF NOT EXISTS guest_categories (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_guest_categories_wedding FOREIGN KEY (wedding_id) REFERENCES weddings(id)
);
CREATE INDEX IF NOT EXISTS idx_guest_categories_wedding ON guest_categories(wedding_id);

-- ---------- Invités ----------
CREATE TABLE IF NOT EXISTS guests (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL,
    category_id BIGINT,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(150),
    address VARCHAR(255),
    allowed_companions INTEGER,
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_guests_wedding FOREIGN KEY (wedding_id) REFERENCES weddings(id),
    CONSTRAINT fk_guests_category FOREIGN KEY (category_id) REFERENCES guest_categories(id)
);
CREATE INDEX IF NOT EXISTS idx_guests_wedding ON guests(wedding_id);
CREATE INDEX IF NOT EXISTS idx_guests_category ON guests(category_id);

-- ============================================================
-- Permissions CATEGORY_* (GUEST_* déjà présentes en V2)
-- ============================================================
INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT * FROM (
    SELECT 'CATEGORY_VIEW' AS code, 'Voir les catégories d''invités' AS libelle, 'GUESTS' AS categorie, NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'CATEGORY_CREATE', 'Créer des catégories d''invités', 'GUESTS', NOW(), NOW()
    UNION ALL SELECT 'CATEGORY_UPDATE', 'Modifier des catégories d''invités', 'GUESTS', NOW(), NOW()
    UNION ALL SELECT 'CATEGORY_DELETE', 'Supprimer des catégories d''invités', 'GUESTS', NOW(), NOW()
) AS v
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CATEGORY_VIEW');

-- Attribution : SUPER_ADMIN (global) et ORGANISATEUR (gestion des mariages)
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ORGANISATEUR')
  AND p.code LIKE 'CATEGORY_%'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);