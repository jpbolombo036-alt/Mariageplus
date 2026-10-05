package com.mariageplus.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Option de couleur de tenue proposée dans les formulaires organisateur.
 * Alimente le sélecteur sans dupliquer les libellés côté front. Le code couleur
 * {@code hex} permet d'afficher une pastille dans le sélecteur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Couleur de tenue demandée aux invités (option de formulaire)")
public class DressCodeOption {

    @Schema(description = "Code technique stocké en base", example = "BLACK")
    private String value;

    @Schema(description = "Libellé français de la couleur", example = "Noir")
    private String label;

    @Schema(description = "Code couleur CSS pour l'aperçu", example = "#1F1F1F")
    private String hex;

    @Schema(description = "Précision affichée sous le libellé",
            example = "Tenue noire : du plus sobre au plus chic")
    private String description;
}