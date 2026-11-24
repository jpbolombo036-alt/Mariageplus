package com.mariageplus.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Message du SUPER_ADMIN. {@code audience} est explicite pour qu'un
 * {@code organizationId} oublié ne devienne jamais un envoi à tous.
 */
@Data
public class AdminMessageRequest {

    public enum Audience { ORGANIZATION, ALL_ORGANIZERS }

    @NotNull(message = "Le public visé est requis")
    private Audience audience;

    /** Requis quand audience = ORGANIZATION, ignoré sinon. */
    private Long organizationId;

    @NotBlank(message = "Le titre est requis")
    @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères")
    private String title;

    @NotBlank(message = "Le message est requis")
    @Size(max = 2000, message = "Le message ne doit pas dépasser 2000 caractères")
    private String message;
}
