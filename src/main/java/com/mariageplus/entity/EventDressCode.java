package com.mariageplus.entity;

/**
 * Couleur de tenue demandée aux invités (facultatif).
 *
 * <p>Le champ porte sur la COULEUR, pas sur un style : l'invité doit voir sans
 * ambiguïté la teinte à porter, quel que soit le type de vêtement choisi
 * (robe, costume, smoking…). Les valeurs plus « style » (CASUAL, COCKTAIL…)
 * ont été remplacées ; une migration V43 remet à null les anciennes valeurs,
 * qui n'ont aucun équivalent.</p>
 *
 * <p>Les libellés français et le code couleur (hex) sont portés par l'enum :
 * source unique pour l'API, la page publique et les formulaires.</p>
 */
public enum EventDressCode {

    BLACK("Noir", "#1F1F1F",
            "Tenue noire : du plus sobre au plus chic"),
    WHITE("Blanc", "#FFFFFF",
            "Tenue blanche"),
    IVORY("Blanc cassé / Ivoire", "#F2E8D5",
            "Blanc cassé ou ivoire — à privilégier pour ne pas ressembler à la robe de la mariée"),
    GREY("Gris", "#9E9E9E",
            "Gris clair, argenté ou anthracite"),
    NAVY("Bleu marine", "#1B2A4A",
            "Bleu marine ou bleu nuit"),
    BEIGE("Beige / Doré", "#D9C08A",
            "Beige, champagne ou doré"),
    RED("Rouge", "#B00020",
            "Rouge, à réserver aux thèmes qui le demandent"),
    PINK("Rose", "#E8A0BF",
            "Rose poudré"),
    COLORFUL("Couleur libre", "#7A5AF8",
            "Tenue colorée ou motif original : l'organisateur précise la teinte exacte");

    private final String label;
    private final String hex;
    private final String description;

    EventDressCode(String label, String hex, String description) {
        this.label = label;
        this.hex = hex;
        this.description = description;
    }

    /** Libellé français de la couleur, affiché dans les listes. */
    public String getLabel() {
        return label;
    }

    /** Code couleur CSS (hex) pour l'aperçu visuel dans les sélecteurs et l'invitation. */
    public String getHex() {
        return hex;
    }

    /** Précision affichée sous le libellé (aide à l'invité). */
    public String getDescription() {
        return description;
    }
}
