package com.mariageplus.dto.drink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vue du catalogue pour un organisateur : chaque boisson du catalogue global
 * avec son statut de disponibilité POUR L'ÉVÉNEMENT demandé.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableDrinkResponse {

    private Long catalogItemId;
    private String name;
    private String description;
    private Integer displayOrder;
    private boolean active;

    /** Déclarée disponible pour cet événement (visible par les invités). */
    private boolean available;

    /** URL publique de la photo (CDN S3 ou endpoint API) ; null si aucune. */
    private String imageUrl;
}
