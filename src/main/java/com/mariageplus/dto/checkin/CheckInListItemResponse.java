package com.mariageplus.dto.checkin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Invité présent dans la salle (agrégat des check-ins d'une invitation) :
 * nom, nombre de personnes entrées, boisson choisie au RSVP et table affectée.
 */
@Getter
@Builder
public class CheckInListItemResponse {
    private Long invitationId;
    private Long guestId;
    private String guestName;
    private Integer numberOfAttendees;
    private LocalDateTime lastCheckedInAt;
    private String tableName;
    private String drinkChoice;
}
