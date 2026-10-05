package com.mariageplus.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Option de tenue vestimentaire proposée dans les formulaires organisateur.
 * Alimente le sélecteur sans dupliquer les libellés français côté front.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenue demandée aux invités (option de formulaire)")
public class DressCodeOption {

    @Schema(description = "Code technique stocké en base", example = "BLACK_TIE")
    private String value;

    @Schema(description = "Libellé français affiché", example = "Cravate noire")
    private String label;

    @Schema(description = "Précision affichée sous le libellé",
            example = "Smoking pour les hommes, robe longue pour les femmes")
    private String description;
}