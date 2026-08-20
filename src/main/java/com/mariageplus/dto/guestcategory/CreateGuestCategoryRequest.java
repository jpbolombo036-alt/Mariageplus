package com.mariageplus.dto.guestcategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGuestCategoryRequest {

    @NotBlank(message = "Le nom de la catégorie est requis")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;

    private Integer displayOrder;
}
