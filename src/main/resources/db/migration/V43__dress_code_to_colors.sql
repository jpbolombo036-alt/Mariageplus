-- Le vestiaire passe de « styles » (CASUAL, COCKTAIL, FORMAL, WHITE, BLACK_TIE,
-- CREATIVE) à des COULEURS (BLACK, WHITE, IVORY, GREY, NAVY, BEIGE, RED, PINK,
-- COLORFUL). Les anciennes valeurs n'ont pas d'équivalent direct : on les remet
-- à NULL plutôt que de les mapper arbitrairement (évite une valeur illisible par
-- l'enum au premier chargement de l'événement).
UPDATE events SET dress_code = NULL WHERE dress_code IS NOT NULL;