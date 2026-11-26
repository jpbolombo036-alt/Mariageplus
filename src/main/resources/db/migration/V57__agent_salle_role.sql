-- ============================================================
-- V57 : rôle AGENT_SALLE (caissier / employé de salle) et permission BOOKING_REFUND
--
-- L'agent de salle prend les réservations et encaisse, sur toutes les salles de
-- l'organisation. Il ne rembourse pas, n'annule pas de paiement (BOOKING_REFUND),
-- ne voit pas les rapports financiers et ne gère ni les salles ni le taux du jour.
-- ============================================================

INSERT INTO roles (code, description, active, created_at, updated_at)
SELECT 'AGENT_SALLE', 'Prend les réservations de salle et encaisse les paiements (sans remboursement ni rapports)', TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'AGENT_SALLE');

INSERT INTO permissions (code, libelle, categorie, created_at, updated_at)
SELECT 'BOOKING_REFUND', 'Rembourser un client ou annuler un paiement de réservation', 'VENUES', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'BOOKING_REFUND');

-- Remboursements et annulations de paiement : gérant (ORGANISATEUR) et SUPER_ADMIN.
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ORGANISATEUR', 'SUPER_ADMIN') AND p.code = 'BOOKING_REFUND'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Agent de salle : réservations, clients, encaissements, consultation des salles et du taux.
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(), NOW()
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AGENT_SALLE'
  AND p.code IN ('VENUE_VIEW', 'VENUE_CLIENT_MANAGE', 'BOOKING_VIEW', 'BOOKING_MANAGE', 'BOOKING_PAYMENT_RECORD')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
