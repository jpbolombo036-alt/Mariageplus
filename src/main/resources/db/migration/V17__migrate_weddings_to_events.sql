-- ============================================================
-- V17 (Phase 2) : migration des données weddings -> events.
-- L'ancien modèle reste intact (coexistence) : les données sont COPIÉES,
-- pas déplacées. Les IDs des mariages sont conservés dans events (la table
-- est vide à ce stade), ce qui simplifie le rattachement des tables filles.
-- Voir docs/DESIGN_EVENT_AS_ROOT.md.
-- ============================================================

-- 1) Copie des mariages vers events (type = WEDDING)
INSERT INTO events (
    id, organization_id, name, type, description, message,
    event_date, venue_name, venue_address, city, commune, country,
    status, display_order, active, created_by, updated_by,
    created_at, updated_at, deleted_at
)
SELECT
    w.id,
    w.organization_id,
    TRIM(BOTH ' ' FROM CONCAT(
        COALESCE(w.groom_first_name, ''), ' ', COALESCE(w.groom_last_name, ''),
        ' & ',
        COALESCE(w.bride_first_name, ''), ' ', COALESCE(w.bride_last_name, '')
    )),
    'WEDDING',
    w.description,
    w.welcome_message,
    (SELECT MIN(we.event_date) FROM wedding_events we
      WHERE we.wedding_id = w.id AND we.deleted_at IS NULL),
    NULL, NULL, NULL, NULL, NULL,
    w.status,
    NULL,
    TRUE,
    w.created_by,
    w.updated_by,
    w.created_at,
    w.updated_at,
    w.deleted_at
FROM weddings w;

-- 2) Détails spécifiques mariage (1-1)
INSERT INTO wedding_details (
    id, event_id, groom_first_name, groom_last_name,
    bride_first_name, bride_last_name,
    groom_photo_url, bride_photo_url, couple_photo_url,
    welcome_message, created_at, updated_at, deleted_at
)
SELECT
    w.id, w.id,
    w.groom_first_name, w.groom_last_name,
    w.bride_first_name, w.bride_last_name,
    w.groom_photo_url, w.bride_photo_url, w.couple_photo_url,
    w.welcome_message,
    w.created_at, w.updated_at, w.deleted_at
FROM weddings w;

-- 3) Sessions : wedding_events -> event_sessions (mêmes IDs conservés)
INSERT INTO event_sessions (
    id, event_id, name, type, description,
    session_date, start_time, end_time,
    venue_name, venue_address, city, commune, country,
    latitude, longitude, map_url,
    display_order, active, created_at, updated_at, deleted_at
)
SELECT
    we.id,
    we.wedding_id,
    we.name,
    we.type,
    we.description,
    we.event_date,
    we.start_time,
    we.end_time,
    we.venue_name,
    we.venue_address,
    we.city,
    we.commune,
    we.country,
    we.latitude,
    we.longitude,
    we.map_url,
    we.display_order,
    we.active,
    we.created_at,
    we.updated_at,
    we.deleted_at
FROM wedding_events we;

-- 4) Reposition des séquences (IDs insérés explicitement) : voir V18,
-- migration spécifique par moteur (H2 en tests, PostgreSQL en production).

