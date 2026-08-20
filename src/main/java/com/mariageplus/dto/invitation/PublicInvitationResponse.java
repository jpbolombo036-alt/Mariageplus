package com.mariageplus.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse publique minimale, utilisée par l'accès par publicToken.
 * Ne contient aucune donnée administrative interne.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicInvitationResponse {

    private String guestFirstName;
    private String guestLastName;

    /** Nom du couple affiché (« Groom & Bride ») — infos mariage minimales pour l'invité. */
    private String weddingDisplayName;

    private String status;
    private String rsvpStatus;
    private Integer rsvpNumberOfAttendees;
}
