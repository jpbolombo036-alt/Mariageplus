package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * État d'une invitation retourné au scan (données strictement nécessaires à
 * l'agent d'accueil). Aucune donnée administrative interne n'est exposée.
 * {@code publicToken} est une donnée déjà publique (lien d'invitation) : il
 * permet d'afficher la carte confirmée enregistrée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInScanResponse {

    private String guestName;
    private String weddingDisplayName;
    private String invitationStatus;
    private String rsvpStatus;
    private int expectedAttendees;
    private int checkedInAttendees;
    private int remainingAttendees;
    private boolean canCheckIn;
    private String tableName;
    private String drinkChoice;

    /** Références utiles à l'agent d'accueil (données publiques de l'invitation). */
    private String publicToken;
    private String invitationCode;
    private boolean hasCard;
    private LocalDateTime checkedInAt;

    // Contexte événement (affichage agent : QUEL ÉVÉNEMENT / QUAND / OÙ)
    private String eventDate;
    private String eventTime;
    private String eventVenue;
}