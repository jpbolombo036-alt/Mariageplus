package com.mariageplus.dto.guest;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ADDITIF (GESTIONNAIRE_INVITES) : réponse RSVP projetée par invité/invitation
 * pour l'administration. Lecture seule, aucun impact sur les DTO existants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RsvpSummaryResponse {

    private Long invitationId;
    private Long guestId;
    private String status;
    private Integer numberOfAttendees;
    private LocalDateTime respondedAt;
}