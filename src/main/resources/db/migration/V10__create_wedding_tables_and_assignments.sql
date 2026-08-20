-- ============================================================
-- V10 : tables de mariage + affectation des invités (Étape 8)
-- Additive, ne modifie pas V1..V9.
-- wedding_tables : capacité par table, nom unique dans un même mariage.
-- table_assignments : un guest ne peut avoir qu'une seule affectation
--   active (UNIQUE guest_id). L'appartenance au même mariage est
--   vérifiée côté service (Guest.wedding == Table.wedding).
-- Compatible PostgreSQL (production) et H2 (tests).
-- ============================================================

CREATE TABLE IF NOT EXISTS wedding_tables (
    id BIGSERIAL PRIMARY KEY,
    wedding_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    capacity INTEGER NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_wedding_tables_wedding FOREIGN KEY (wedding_id) REFERENCES weddings(id),
    CONSTRAINT uk_wedding_tables_name UNIQUE (wedding_id, name)
);

CREATE INDEX IF NOT EXISTS idx_wedding_tables_wedding ON wedding_tables(wedding_id);

CREATE TABLE IF NOT EXISTS table_assignments (
    id BIGSERIAL PRIMARY KEY,
    wedding_table_id BIGINT NOT NULL,
    guest_id BIGINT NOT NULL,
    assigned_by BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_table_assignments_table FOREIGN KEY (wedding_table_id) REFERENCES wedding_tables(id),
    CONSTRAINT fk_table_assignments_guest FOREIGN KEY (guest_id) REFERENCES guests(id),
    CONSTRAINT uk_table_assignments_guest UNIQUE (guest_id)
);

CREATE INDEX IF NOT EXISTS idx_table_assignments_table ON table_assignments(wedding_table_id);
CREATE INDEX IF NOT EXISTS idx_table_assignments_guest ON table_assignments(guest_id);