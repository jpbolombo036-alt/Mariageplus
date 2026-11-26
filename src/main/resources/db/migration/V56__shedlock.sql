-- ============================================================
-- V56 : verrou partagé des tâches planifiées (ShedLock)
-- Une ligne par tâche ; lock_until dit jusqu'à quand une instance la détient.
-- ============================================================

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
