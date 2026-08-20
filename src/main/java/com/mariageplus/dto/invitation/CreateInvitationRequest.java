package com.mariageplus.dto.invitation;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Création d'une invitation. Le client fournit uniquement le guestId : le
 * weddingId, l'invitationCode et le publicToken sont déterminés côté backend.
 */
@Data
public class CreateInvitationRequest {

    @NotNull(message = "L'invité est requis")
    private Long guestId;
}
