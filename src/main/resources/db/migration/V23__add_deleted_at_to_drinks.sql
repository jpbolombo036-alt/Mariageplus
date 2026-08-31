-- ============================================================
-- V23 : ajoute deleted_at à la table drinks (soft-delete BaseEntity)
-- ============================================================
-- L'entité Drink hérite de BaseEntity (@SQLRestriction "deleted_at IS NULL").
-- V21 avait créé la table sans cette colonne, ce qui faisait échouer la
-- validation Hibernate (ddl-auto: validate) au démarrage.

ALTER TABLE drinks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
