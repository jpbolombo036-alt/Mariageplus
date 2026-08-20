package com.mariageplus.dto.wedding;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Changement de statut d'un mariage. Le statut est fourni en texte et validé /
 * converti côté service afin de produire une erreur 400 cohérente si invalide.
 */
@Data
public class UpdateWeddingStatusRequest {

    @NotBlank(message = "Le statut est requis")
    private String status;
}
