-- V53 : rappels automatiques des événements proches dans la cloche des organisateurs.

-- Journal : un seul envoi par événement, type de rappel et date, même après un
-- redémarrage ou avec plusieurs instances. Si la date de l'événement change, les
-- rappels repartent pour la nouvelle date.
CREATE TABLE IF NOT EXISTS event_reminder_log (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    event_date DATE NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_event_reminder UNIQUE (event_id, kind, event_date)
);

-- Réglages à trois niveaux : GLOBAL (scope_id = 0), ORGANIZATION, EVENT.
-- NULL = hériter du niveau supérieur (événement → organisation → global → serveur).
-- Le fuseau horaire n'existe qu'aux niveaux global et organisation.
CREATE TABLE IF NOT EXISTS reminder_settings (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL,
    enabled BOOLEAN,
    first_reminder_enabled BOOLEAN,
    first_reminder_days INT,
    eve_enabled BOOLEAN,
    day_of_enabled BOOLEAN,
    send_hour INT,
    time_zone VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT uq_reminder_settings_scope UNIQUE (scope, scope_id),
    CONSTRAINT ck_reminder_first_days CHECK (first_reminder_days IS NULL OR first_reminder_days BETWEEN 2 AND 30),
    CONSTRAINT ck_reminder_send_hour CHECK (send_hour IS NULL OR send_hour BETWEEN 0 AND 23)
);

-- Lien direct vers l'événement depuis la cloche.
ALTER TABLE user_notifications ADD COLUMN IF NOT EXISTS event_id BIGINT;
