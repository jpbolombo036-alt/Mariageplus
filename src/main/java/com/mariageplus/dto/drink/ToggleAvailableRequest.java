package com.mariageplus.dto.drink;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bascule de disponibilité d'une boisson du catalogue pour un événement
 * (organisateur). La sécurité réelle est côté backend (isSuperAdmin ou
 * scope organisation).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToggleAvailableRequest {

    @NotNull(message = "catalogItemId est requis")
    private Long catalogItemId;

    @NotNull(message = "available est requis (true/false)")
    private Boolean available;
}
