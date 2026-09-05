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

    // Visuel & message (depuis Wedding)
    private String couplePhotoUrl;
    private String groomPhotoUrl;
    private String bridePhotoUrl;
    private String message;

    // Événement principal (date / heure / lieu formatés en français)
    private String eventName;
    private String eventDate;
    private String eventStartTime;
    private String eventVenue;

    /** Maximum de participants accepté : 1 + allowedCompanions (résolu côté backend). */
    private Integer maxAccepted;

    private String status;
    private String rsvpStatus;
    private Integer rsvpNumberOfAttendees;
    private String rsvpDrinkChoice;

    /** Choix multiples de boissons du RSVP (pré-remplissage du formulaire invité). */
    private java.util.List<String> rsvpDrinkChoices;

    /** Token public de l'invitation, nécessaire pour afficher le QR code côté front. */
    private String publicToken;

    /** Programme de la journée : sessions actives de l'événement, triées (additif — peut être vide). */
    private java.util.List<PublicEventSessionResponse> sessions;
}
