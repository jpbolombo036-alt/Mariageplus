package com.mariageplus.dto.wedding;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Modification d'un mariage : tous les champs sont optionnels (mise à jour partielle).
 */
@Data
public class UpdateWeddingRequest {

    @Size(max = 100, message = "Le prénom du marié ne doit pas dépasser 100 caractères")
    private String groomFirstName;

    @Size(max = 100, message = "Le nom du marié ne doit pas dépasser 100 caractères")
    private String groomLastName;

    @Size(max = 100, message = "Le prénom de la mariée ne doit pas dépasser 100 caractères")
    private String brideFirstName;

    @Size(max = 100, message = "Le nom de la mariée ne doit pas dépasser 100 caractères")
    private String brideLastName;

    @Size(max = 1000, message = "L'URL photo marié ne doit pas dépasser 1000 caractères")
    private String groomPhotoUrl;

    @Size(max = 1000, message = "L'URL photo mariée ne doit pas dépasser 1000 caractères")
    private String bridePhotoUrl;

    @Size(max = 1000, message = "L'URL photo du couple ne doit pas dépasser 1000 caractères")
    private String couplePhotoUrl;

    @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
    private String description;

    @Size(max = 2000, message = "Le message de bienvenue ne doit pas dépasser 2000 caractères")
    private String welcomeMessage;
}
