-- Le vestiaire devient une LISTE de couleurs (max 3) au lieu d'une couleur
-- unique, et gagne une photo (pagne / tissu à porter — mariages coutumiers).
-- La colonne dress_code, unused et vide, est remplacée.
ALTER TABLE events DROP COLUMN IF EXISTS dress_code;
ALTER TABLE events ADD COLUMN IF NOT EXISTS dress_colors VARCHAR(60);
ALTER TABLE events ADD COLUMN IF NOT EXISTS dress_image_key VARCHAR(255);
-- Repli en base quand le stockage objet est désactivé (dev/tests), comme `image`.
ALTER TABLE events ADD COLUMN IF NOT EXISTS dress_image BYTEA;