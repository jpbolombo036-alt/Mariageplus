package com.mariageplus.dto.drink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Boisson du catalogue global (superadmin) — liste admin. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogDrinkResponse {

    private Long id;
    private String name;
    private String description;
    private Integer displayOrder;
    private boolean active;

    /** URL publique de la photo (CDN S3 ou endpoint API) ; null si aucune. */
    private String imageUrl;
}
