package com.mariageplus.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Résultat d'un envoi ou d'un renvoi. {@code publicInviteUrl} est destiné à
 * l'organisateur (partage manuel si l'email n'a pas pu partir).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendInvitationResponse {

    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime lastSentAt;
    private boolean emailSent;
    private String publicInviteUrl;
}
