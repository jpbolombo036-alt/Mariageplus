package com.mariageplus.dto.rsvp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse publique après soumission d'un RSVP. Ne contient aucune donnée
 * administrative interne.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicRsvpResponse {

    private String invitationStatus;
    private String rsvpStatus;
    private Integer numberOfAttendees;
    private LocalDateTime respondedAt;
    private String drinkChoice;

    /** Choix multiples de boissons retenus (jusqu'à 3). */
    private java.util.List<String> drinkChoices;
}
