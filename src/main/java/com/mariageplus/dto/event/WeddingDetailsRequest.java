package com.mariageplus.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Fiche de détails spécifiques au mariage (imbriquée dans les requêtes
 * Create/Update d'un événement de type WEDDING).
 */
@Data
public class WeddingDetailsRequest {

    @NotBlank(message = "Le prénom du marié est requis")
    @Size(max = 100, message = "Le prénom du marié ne doit pas dépasser 100 caractères")
    private String groomFirstName;

    @NotBlank(message = "Le nom du marié est requis")
    @Size(max = 100, message = "Le nom du marié ne doit pas dépasser 100 caractères")
    private String groomLastName;

    @NotBlank(message = "Le prénom de la mariée est requis")
    @Size(max = 100, message = "Le prénom de la mariée est requis et ne doit pas dépasser 100 caractères")
    private String brideFirstName;

    @NotBlank(message = "Le nom de la mariée est requis")
    @Size(max = 100, message = "Le nom de la mariée ne doit pas dépasser 100 caractères")
    private String brideLastName;

    @Size(max = 1000, message = "L'URL photo marié ne doit pas dépasser 1000 caractères")
    private String groomPhotoUrl;

    @Size(max = 1000, message = "L'URL photo mariée ne doit pas dépasser 1000 caractères")
    private String bridePhotoUrl;

    @Size(max = 1000, message = "L'URL photo du couple ne doit pas dépasser 1000 caractères")
    private String couplePhotoUrl;

    @Size(max = 2000, message = "Le message de bienvenue ne doit pas dépasser 2000 caractères")
    private String welcomeMessage;
}
