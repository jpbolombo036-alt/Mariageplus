-- ============================================================
-- V29 : Accorder EVENT_VIEW aux rôles opérationnels
-- GESTIONNAIRE_INVITES et AGENT_ACCUEIL n'ont pas WEDDING_VIEW ni
-- EVENT_VIEW dans le seed d'origine (V2) : la liste des événements
-- (/api/events -> EventService.list exige EVENT_VIEW) leur renvoyait 403,
-- rendant impossible l'accès au check-in ou aux invités depuis l'écran web.
-- Idempotent (portable PostgreSQL / H2).
-- ============================================================

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('GESTIONNAIRE_INVITES', 'AGENT_ACCUEIL')
  AND p.code = 'EVENT_VIEW'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);