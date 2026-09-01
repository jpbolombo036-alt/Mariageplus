package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Résultat de la recherche invité pour l'agent d'accueil : état invitation +
 * RSVP + check-in, avec le publicToken (donnée déjà publique) permettant
 * d'afficher la carte d'invitation confirmée enregistrée. Aucune donnée
 * administrative interne sensible n'est exposée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInSearchItemResponse {

    private String guestName;
    private String phone;
    private String invitationCode;
    private String invitationStatus;   // GENERATED / SENT / ... (null si sans invitation)
    private String rsvpStatus;         // ACCEPTED / DECLINED / null (en attente)
    private int expectedAttendees;     // RSVP.numberOfAttendees si ACCEPTED sinon 0
    private int checkedInAttendees;    // somme des entrées
    private int remainingAttendees;
    private boolean canCheckIn;
    private LocalDateTime checkedInAt; // dernier check-in si existant
    private String tableName;
    private String drinkChoice;
    private String publicToken;        // lien public + carte confirmée
    private boolean hasCard;

    // Contexte événement (affichage agent : QUI / QUEL ÉVÉNEMENT / QUAND / OÙ)
    private String eventName;
    private String eventDate;
    private String eventTime;
    private String eventVenue;
}