package com.mariageplus.dto.table;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Création d'une table de mariage.
 */
@Data
public class CreateWeddingTableRequest {

    @NotBlank(message = "Le nom de la table est requis")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    @NotNull(message = "La capacité est requise")
    @Min(value = 1, message = "La capacité doit être au moins 1")
    private Integer capacity;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;
}