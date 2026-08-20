package com.mariageplus.dto.table;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Déplacement d'une affectation vers une autre table. Le backend détermine
 * toujours le mariage/périmètre et vérifie la capacité de la cible.
 */
@Data
public class MoveGuestRequest {

    @NotNull(message = "La table cible est requise")
    private Long tableId;
}