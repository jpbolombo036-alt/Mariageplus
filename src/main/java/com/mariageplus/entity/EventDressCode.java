package com.mariageplus.entity;

/**
 * Tenue demandée aux invités (facultatif).
 *
 * <p>Les libellés français sont portés par l'enum : c'est la source unique pour
 * l'API, la page publique et les formulaires (aucun libellé codé en dur côté
 * front). Le code technique reste stable en base.</p>
 */
public enum EventDressCode {

    CASUAL("Tenue décontractée",
            "Jeans, t-shirt, baskets ou chaussures décontractées, mais propres"),
    COCKTAIL("Tenue cocktail",
            "Robe courte ou costume accessorisé, sans smoking ni robe de bal"),
    FORMAL("Tenue de soirée",
            "Tenue élégante et sobre : chemise ou robe midi"),
    WHITE("Tenue blanche",
            "Tenue entièrement ou principalement blanche"),
    BLACK_TIE("Cravate noire",
            "Smoking pour les hommes, robe longue pour les femmes"),
    CREATIVE("Tenue créative / colorée",
            "Couleur ou motif original (rouge, vert, fleuri…)");

    private final String label;
    private final String description;

    EventDressCode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Libellé français court, affiché dans les listes. */
    public String getLabel() {
        return label;
    }

    /** Précision affichée sous le libellé (aide à l'invité). */
    public String getDescription() {
        return description;
    }
}
