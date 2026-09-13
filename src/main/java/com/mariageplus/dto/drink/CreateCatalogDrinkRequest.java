package com.mariageplus.dto.drink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Création / édition d'une boisson du catalogue global (SUPER_ADMIN). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCatalogDrinkRequest {

    @NotBlank(message = "Le nom est requis")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères")
    private String name;

    @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
    private String description;

    private Integer displayOrder;
}
