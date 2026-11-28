-- ============================================================
-- V58 : en-tête des devis et reçus de salle (une ligne par organisation)
-- Raison sociale, mentions légales RDC (RCCM, Id. Nat., NIF), coordonnées,
-- pied de page libre et logo (S3 si configuré, sinon en base).
-- ============================================================

CREATE TABLE IF NOT EXISTS venue_document_settings (
    organization_id BIGINT PRIMARY KEY REFERENCES organizations(id),
    legal_name VARCHAR(150),
    rccm VARCHAR(60),
    id_nat VARCHAR(60),
    nif VARCHAR(60),
    address VARCHAR(255),
    phone VARCHAR(60),
    email VARCHAR(150),
    footer_note VARCHAR(500),
    logo_key VARCHAR(500),
    logo BYTEA,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
