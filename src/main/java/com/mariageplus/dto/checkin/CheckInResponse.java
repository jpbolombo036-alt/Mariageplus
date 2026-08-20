package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse après un enregistrement de check-in : l'opération effectuée + l'état
 * mis à jour (attendus / déjà entrés / restants). Aucun identifiant interne
 * sensible (invitation/wedding/guest) n'est exposé en dehors de l'id du check-in.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {

    private Long checkInId;
    private String guestName;
    private String weddingDisplayName;
    private String invitationStatus;
    private String rsvpStatus;
    private int numberOfAttendees;
    private LocalDateTime checkedInAt;
    private int expectedAttendees;
    private int checkedInAttendees;
    private int remainingAttendees;
}