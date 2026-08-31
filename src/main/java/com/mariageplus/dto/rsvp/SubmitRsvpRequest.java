package com.mariageplus.dto.rsvp;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Soumission d'une réponse RSVP via l'accès public. Seuls le statut, le nombre
 * de participants et le choix de boisson sont acceptés : ni guestId, ni weddingId,
 * ni invitationId, ni publicToken, ni maximumAllowed (le maximum est calculé côté backend).
 */
@Data
public class SubmitRsvpRequest {

    @NotBlank(message = "La réponse est requise")
    private String status;

    @NotNull(message = "Le nombre de participants est requis")
    @Min(value = 0, message = "Le nombre de participants ne peut pas être négatif")
    private Integer numberOfAttendees;

    private String drinkChoice;
}
