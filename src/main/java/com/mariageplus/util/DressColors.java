package com.mariageplus.util;

import com.mariageplus.dto.event.DressCodeOption;
import com.mariageplus.entity.EventDressCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sérialisation des couleurs de tenue demandées pour un événement.
 *
 * <p>Même approche que les choix de boissons du RSVP ({@code Rsvp.drinkChoices}) :
 * une valeur texte à virgules, ici un maximum de {@value #MAX} couleurs. Un
 * pagne de mariage coutumier combine souvent plusieurs teintes, et l'organisateur
 * doit rester libre de n'en imposer qu'une seule — d'où la liste, pas l'enum
 * unique.</p>
 */
public final class DressColors {

    /** Nombre maximum de couleurs de tenue demandées pour un événement. */
    public static final int MAX = 3;

    private DressColors() {
    }

    /**
     * Normalise et contrôle la saisie : sans doublon, ordre de saisie conservé,
     * maximum {@value #MAX} couleurs. Renvoie le CSV à stocker (null si vide).
     *
     * @throws IllegalArgumentException si plus de {@value #MAX} couleurs distinctes
     */
    public static String encode(List<EventDressCode> colors) {
        if (colors == null) {
            return null;
        }
        LinkedHashSet<EventDressCode> unique = new LinkedHashSet<>();
        for (EventDressCode c : colors) {
            if (c != null) {
                unique.add(c);
            }
        }
        if (unique.isEmpty()) {
            return null;
        }
        if (unique.size() > MAX) {
            throw new IllegalArgumentException(
                    "Vous pouvez demander au maximum " + MAX + " couleurs de tenue");
        }
        return unique.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** CSV stocké → codes, dans l'ordre de saisie (valeurs vides et doublons ignorés). */
    public static List<String> codes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String code = raw.trim();
            if (!code.isEmpty() && !out.contains(code)) {
                out.add(code);
            }
        }
        return out;
    }

    /**
     * CSV stocké → options d'affichage (libellé FR + couleur d'aperçu), dans
     * l'ordre de saisie. Une valeur inconnue est ignorée plutôt que fatale : la
     * page d'invitation ne doit jamais casser sur une palette qui a évolué.
     */
    public static List<DressCodeOption> options(String csv) {
        List<DressCodeOption> out = new ArrayList<>();
        for (String code : codes(csv)) {
            try {
                EventDressCode c = EventDressCode.valueOf(code);
                out.add(DressCodeOption.builder()
                        .value(c.name())
                        .label(c.getLabel())
                        .hex(c.getHex())
                        .description(c.getDescription())
                        .build());
            } catch (IllegalArgumentException ignored) {
                // Valeur retirée de la palette : on l'ignore silencieusement.
            }
        }
        return out;
    }
}