-- Phase 1 (coexistence) : création des tables du nouveau modèle « Event » racine.
-- L'ancien modèle weddings / wedding_events reste intact ; la migration de
-- données (weddings -> events) interviendra en Phase 2 (voir
-- docs/DESIGN_EVENT_AS_ROOT.md).

-- 1) Table racine des événements (tous types)
CREATE TABLE events (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name            VARCHAR(150) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    description     VARCHAR(2000),
    message         VARCHAR(2000),
    event_date      DATE,
    start_time      TIME,
    end_time        TIME,
    venue_name      VARCHAR(200),
    venue_address   VARCHAR(255),
    city            VARCHAR(100),
    commune         VARCHAR(100),
    country         VARCHAR(100),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    map_url         VARCHAR(1000),
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    display_order   INTEGER,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      BIGINT,
    updated_by      BIGINT,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

CREATE INDEX idx_events_org ON events (organization_id);
CREATE INDEX idx_events_org_date ON events (organization_id, event_date);
CREATE INDEX idx_events_org_type ON events (organization_id, type);

-- 2) Détails spécifiques au mariage (1-1 avec events, type = WEDDING)
CREATE TABLE wedding_details (
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT NOT NULL UNIQUE,
    groom_first_name VARCHAR(100),
    groom_last_name  VARCHAR(100),
    bride_first_name VARCHAR(100),
    bride_last_name  VARCHAR(100),
    groom_photo_url  VARCHAR(1000),
    bride_photo_url  VARCHAR(1000),
    couple_photo_url VARCHAR(1000),
    welcome_message  VARCHAR(2000),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    deleted_at       TIMESTAMP,
    CONSTRAINT fk_wedding_details_event FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE INDEX idx_wedding_details_event ON wedding_details (event_id);

-- 3) Sessions (sous-étapes) d'un événement — remplace wedding_events
CREATE TABLE event_sessions (
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT NOT NULL,
    name          VARCHAR(100) NOT NULL,
    type          VARCHAR(30) NOT NULL,
    description   VARCHAR(1000),
    session_date  DATE,
    start_time    TIME,
    end_time      TIME,
    venue_name    VARCHAR(200),
    venue_address VARCHAR(255),
    city          VARCHAR(100),
    commune       VARCHAR(100),
    country       VARCHAR(100),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    map_url       VARCHAR(1000),
    display_order INTEGER,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    deleted_at    TIMESTAMP,
    CONSTRAINT fk_event_sessions_event FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE INDEX idx_event_sessions_event ON event_sessions (event_id);
CREATE INDEX idx_event_sessions_event_date ON event_sessions (event_id, session_date);
