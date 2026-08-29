package com.mariageplus.dto.event;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Changement de statut d'un événement (transitions validées côté service,
 * mêmes règles que les mariages).
 */
@Data
public class UpdateEventStatusRequest {

    @NotBlank(message = "Le statut est requis")
    private String status;
}
