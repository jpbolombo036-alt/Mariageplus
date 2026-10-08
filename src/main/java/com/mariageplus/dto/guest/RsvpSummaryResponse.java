package com.mariageplus.dto.guest;

import java.time.LocalDateTime;
import java.util.List;

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
    /**
     * Choix de boissons de l'invité (1 à 3) — sert à prévoir les achats avant
     * le jour J. Même logique d'affichage que RsvpService : JSON d'abord,
     * fallback sur le choix unique historique.
     */
    private List<String> drinkChoices;
}