-- ============================================================
-- V22 : seed des permissions DRINK pour les bases existantes
-- ============================================================

INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT v.code, v.libelle, v.categorie, v.created_at, v.updated_at FROM (
    SELECT 'DRINK_VIEW' AS code, 'Voir les boissons' AS libelle, 'DRINKS' AS categorie, NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'DRINK_CREATE', 'Créer des boissons', 'DRINKS', NOW(), NOW()
    UNION ALL SELECT 'DRINK_UPDATE', 'Modifier des boissons', 'DRINKS', NOW(), NOW()
    UNION ALL SELECT 'DRINK_DELETE', 'Supprimer des boissons', 'DRINKS', NOW(), NOW()
) AS v
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code IN ('DRINK_VIEW', 'DRINK_CREATE', 'DRINK_UPDATE', 'DRINK_DELETE'));

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ORGANISATEUR'
  AND p.code IN ('DRINK_VIEW', 'DRINK_CREATE', 'DRINK_UPDATE', 'DRINK_DELETE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN ('DRINK_VIEW', 'DRINK_CREATE', 'DRINK_UPDATE', 'DRINK_DELETE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
