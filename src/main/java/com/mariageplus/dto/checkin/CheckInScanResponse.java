package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * État d'une invitation retourné au scan (données strictement nécessaires à
 * l'agent d'accueil). Aucune donnée administrative interne n'est exposée.
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
}