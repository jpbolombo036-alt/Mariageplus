-- ============================================================
-- V2 : Système RBAC — permissions granulaires + seed des 4 rôles
-- Idempotent (portable PostgreSQL / H2) : INSERT ... WHERE NOT EXISTS
-- ============================================================

CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    libelle VARCHAR(200) NOT NULL,
    categorie VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission ON role_permissions(permission_id);

-- ============================================================
-- Seed des 4 rôles initiaux (si absent)
-- ============================================================
INSERT INTO roles (code, description, active, created_at, updated_at)
SELECT * FROM (
    SELECT 'SUPER_ADMIN' AS code, 'Administrateur global de la plateforme' AS description, TRUE AS active, NOW() AS created_at, NOW() AS updated_at
    UNION ALL
    SELECT 'ORGANISATEUR', 'Propriétaire d''un espace (marié, wedding planner, agence)', TRUE, NOW() AS created_at, NOW() AS updated_at
    UNION ALL
    SELECT 'GESTIONNAIRE_INVITES', 'Gère les invités, invitations et RSVP', TRUE, NOW() AS created_at, NOW() AS updated_at
    UNION ALL
    SELECT 'AGENT_ACCUEIL', 'Contrôle l''accueil et le check-in le jour J', TRUE, NOW() AS created_at, NOW() AS updated_at
) AS v
WHERE NOT EXISTS (SELECT 1 FROM roles);

-- ============================================================
-- Catalogue des permissions (si absent)
-- ============================================================
INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT * FROM (
    SELECT 'USER_VIEW' AS code, 'Voir les utilisateurs' AS libelle, 'USERS' AS categorie, NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'USER_CREATE', 'Créer des utilisateurs', 'USERS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'USER_UPDATE', 'Modifier des utilisateurs', 'USERS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'USER_DELETE', 'Supprimer des utilisateurs', 'USERS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'USER_ACTIVATE', 'Activer des utilisateurs', 'USERS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'USER_DEACTIVATE', 'Désactiver des utilisateurs', 'USERS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ROLE_VIEW', 'Voir les rôles', 'ROLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ROLE_CREATE', 'Créer des rôles', 'ROLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ROLE_UPDATE', 'Modifier des rôles', 'ROLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ROLE_DELETE', 'Supprimer des rôles', 'ROLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'PERMISSION_VIEW', 'Voir les permissions', 'ROLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'PERMISSION_ASSIGN', 'Assigner des permissions', 'ROLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ORGANIZATION_VIEW', 'Voir les organisations', 'ORGANIZATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ORGANIZATION_CREATE', 'Créer des organisations', 'ORGANIZATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ORGANIZATION_UPDATE', 'Modifier des organisations', 'ORGANIZATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ORGANIZATION_DELETE', 'Supprimer des organisations', 'ORGANIZATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'ORGANIZATION_MANAGE_MEMBERS', 'Gérer les membres d''une organisation', 'ORGANIZATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'WEDDING_VIEW', 'Voir les mariages', 'WEDDINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'WEDDING_CREATE', 'Créer des mariages', 'WEDDINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'WEDDING_UPDATE', 'Modifier des mariages', 'WEDDINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'WEDDING_DELETE', 'Supprimer des mariages', 'WEDDINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'WEDDING_PUBLISH', 'Publier un mariage', 'WEDDINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'WEDDING_ARCHIVE', 'Archiver un mariage', 'WEDDINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'EVENT_VIEW', 'Voir les événements', 'EVENTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'EVENT_CREATE', 'Créer des événements', 'EVENTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'EVENT_UPDATE', 'Modifier des événements', 'EVENTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'EVENT_DELETE', 'Supprimer des événements', 'EVENTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'GUEST_VIEW', 'Voir les invités', 'GUESTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'GUEST_CREATE', 'Créer des invités', 'GUESTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'GUEST_UPDATE', 'Modifier des invités', 'GUESTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'GUEST_DELETE', 'Supprimer des invités', 'GUESTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'GUEST_IMPORT', 'Importer des invités', 'GUESTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'GUEST_EXPORT', 'Exporter des invités', 'GUESTS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_VIEW', 'Voir les invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_CREATE', 'Créer des invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_UPDATE', 'Modifier des invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_DELETE', 'Supprimer des invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_SEND', 'Envoyer des invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_RESEND', 'Renvoyer des invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'INVITATION_CANCEL', 'Annuler des invitations', 'INVITATIONS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'RSVP_VIEW', 'Voir les réponses RSVP', 'RSVP', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'RSVP_MANAGE', 'Gérer les réponses RSVP', 'RSVP', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'CHECKIN_VIEW', 'Voir les check-ins', 'CHECKIN', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'CHECKIN_SCAN', 'Scanner les QR codes', 'CHECKIN', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'CHECKIN_CREATE', 'Enregistrer une entrée', 'CHECKIN', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'CHECKIN_CANCEL', 'Annuler un check-in', 'CHECKIN', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'TABLE_VIEW', 'Voir les tables', 'TABLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'TABLE_CREATE', 'Créer des tables', 'TABLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'TABLE_UPDATE', 'Modifier des tables', 'TABLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'TABLE_DELETE', 'Supprimer des tables', 'TABLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'TABLE_ASSIGN_GUEST', 'Affecter un invité à une table', 'TABLES', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'DASHBOARD_VIEW', 'Voir le tableau de bord', 'DASHBOARD', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'STATISTICS_VIEW', 'Voir les statistiques', 'DASHBOARD', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'REPORT_VIEW', 'Voir les rapports', 'DASHBOARD', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'REPORT_EXPORT', 'Exporter les rapports', 'DASHBOARD', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'SETTINGS_VIEW', 'Voir les paramètres', 'SETTINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'SETTINGS_UPDATE', 'Modifier les paramètres', 'SETTINGS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'DRINK_VIEW', 'Voir les boissons', 'DRINKS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'DRINK_CREATE', 'Créer des boissons', 'DRINKS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'DRINK_UPDATE', 'Modifier des boissons', 'DRINKS', NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'DRINK_DELETE', 'Supprimer des boissons', 'DRINKS', NOW() AS created_at, NOW() AS updated_at
 ) AS v
WHERE NOT EXISTS (SELECT 1 FROM permissions);

-- ============================================================
-- Associations rôles <-> permissions (si absentes)
-- ============================================================

-- SUPER_ADMIN : toutes les permissions (accès global)
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ORGANISATEUR : gestion de ses mariages, invités, invitations, RSVP, tables,
-- équipe (membres) et statistiques, dans son périmètre.
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ORGANISATEUR'
  AND p.code IN (
    'ORGANIZATION_VIEW','ORGANIZATION_MANAGE_MEMBERS','PERMISSION_VIEW',
    'WEDDING_VIEW','WEDDING_CREATE','WEDDING_UPDATE','WEDDING_PUBLISH','WEDDING_ARCHIVE',
    'EVENT_VIEW','EVENT_CREATE','EVENT_UPDATE','EVENT_DELETE',
    'GUEST_VIEW','GUEST_CREATE','GUEST_UPDATE','GUEST_DELETE','GUEST_IMPORT','GUEST_EXPORT',
    'INVITATION_VIEW','INVITATION_CREATE','INVITATION_UPDATE','INVITATION_DELETE',
    'INVITATION_SEND','INVITATION_RESEND','INVITATION_CANCEL',
    'RSVP_VIEW','RSVP_MANAGE',
    'CHECKIN_VIEW','CHECKIN_SCAN','CHECKIN_CREATE','CHECKIN_CANCEL',
    'TABLE_VIEW','TABLE_CREATE','TABLE_UPDATE','TABLE_DELETE','TABLE_ASSIGN_GUEST',
    'DASHBOARD_VIEW','STATISTICS_VIEW','REPORT_VIEW','REPORT_EXPORT',
    'SETTINGS_VIEW','SETTINGS_UPDATE',
    'DRINK_VIEW','DRINK_CREATE','DRINK_UPDATE','DRINK_DELETE'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- GESTIONNAIRE_INVITES : invités, invitations, RSVP (pas de gestion du mariage)
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'GESTIONNAIRE_INVITES'
  AND p.code IN (
    'GUEST_VIEW','GUEST_CREATE','GUEST_UPDATE','GUEST_DELETE','GUEST_IMPORT','GUEST_EXPORT',
    'INVITATION_VIEW','INVITATION_CREATE','INVITATION_UPDATE','INVITATION_DELETE',
    'INVITATION_SEND','INVITATION_RESEND','INVITATION_CANCEL',
    'RSVP_VIEW','RSVP_MANAGE',
    'CHECKIN_VIEW','DASHBOARD_VIEW'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- AGENT_ACCUEIL : lecture invitation/invité + check-in
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW() AS created_at, NOW() AS updated_at
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AGENT_ACCUEIL'
  AND p.code IN (
    'GUEST_VIEW','INVITATION_VIEW',
    'CHECKIN_VIEW','CHECKIN_SCAN','CHECKIN_CREATE','CHECKIN_CANCEL',
    'DASHBOARD_VIEW'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

