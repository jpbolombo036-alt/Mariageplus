-- ============================================================
-- V37 : Les réglages plateforme (SETTINGS_VIEW / SETTINGS_UPDATE)
-- sont réservés au SUPER_ADMIN.
-- La migration V2 attribuait SETTINGS_VIEW et SETTINGS_UPDATE au rôle
-- ORGANISATEUR par erreur : l'organisateur voyait l'entrée « Paramètres »
-- (interrupteur WhatsApp global de TOUTE la plateforme). On retire ces
-- deux permissions de tous les rôles sauf SUPER_ADMIN. Idempotent.
-- ============================================================

DELETE FROM role_permissions
WHERE permission_id IN (
        SELECT id FROM permissions WHERE code IN ('SETTINGS_VIEW', 'SETTINGS_UPDATE')
      )
  AND role_id IN (
        SELECT id FROM roles WHERE code <> 'SUPER_ADMIN'
      );
