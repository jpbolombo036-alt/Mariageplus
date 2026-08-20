-- ============================================================
-- V13 : CATEGORY_* pour GESTIONNAIRE_INVITES
-- Additive. SUPER_ADMIN et ORGANISATEUR les ont déjà (V5).
-- ============================================================

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'GESTIONNAIRE_INVITES'
  AND p.code IN ('CATEGORY_VIEW', 'CATEGORY_CREATE', 'CATEGORY_UPDATE', 'CATEGORY_DELETE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
