package com.mariageplus.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse administrative d'une invitation. Le {@code publicToken} (donnée
 * sensible d'accès public) n'est PAS exposé par cette réponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {

    private Long id;
    private Long weddingId;
    private Long guestId;
    private String invitationCode;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime lastSentAt;
    private int reminderCount;
    private LocalDateTime openedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
