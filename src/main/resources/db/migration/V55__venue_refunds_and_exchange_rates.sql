-- ============================================================
-- V55 : module « Salles » — remboursements et taux de change
--
-- 1. Un mouvement d'argent est un PAIEMENT (encaissement) ou un
--    REMBOURSEMENT (sortie, après annulation). Les lignes existantes
--    sont des paiements.
-- 2. Taux de change USD → CDF saisi par la salle (historique conservé) :
--    il remplace la saisie libre à chaque paiement en autre devise.
-- ============================================================

ALTER TABLE venue_booking_payments ADD COLUMN IF NOT EXISTS kind VARCHAR(10) NOT NULL DEFAULT 'PAYMENT';

CREATE TABLE IF NOT EXISTS venue_exchange_rates (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    cdf_per_usd NUMERIC(14,4) NOT NULL,
    effective_at TIMESTAMP NOT NULL,
    set_by BIGINT,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_venue_exchange_rates_positive CHECK (cdf_per_usd > 0)
);
CREATE INDEX IF NOT EXISTS idx_venue_exchange_rates_org ON venue_exchange_rates(organization_id, effective_at);
