-- Photos des boissons : clé S3 (stockage objet) avec fallback base de données,
-- même mécanique que les photos de couverture d'événement (cf. V26).
ALTER TABLE drinks ADD COLUMN IF NOT EXISTS image_key VARCHAR(500);
ALTER TABLE drinks ADD COLUMN IF NOT EXISTS image BYTEA;

-- Choix multiples de boissons au RSVP (jusqu'à 3) : tableau JSON des noms
-- (ex. ["Coca-Cola","Jus d'orange"]). Le champ historique drink_choice
-- (VARCHAR 100) reste alimenté avec la jointure des noms pour compatibilité
-- check-in / dashboard.
ALTER TABLE rsvps ADD COLUMN IF NOT EXISTS drink_choices TEXT;