package com.mariageplus.dto.table;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Mise à jour d'une table (champs optionnels, appliqués s'ils sont fournis).
 */
@Data
public class UpdateWeddingTableRequest {

    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    @Min(value = 1, message = "La capacité doit être au moins 1")
    private Integer capacity;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;
}