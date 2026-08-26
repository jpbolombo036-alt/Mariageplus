package com.mariageplus.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Données complètes pour la page web publique d'invitation ({@code /invitations/{token}}).
 * Distinct de {@link PublicInvitationResponse} (JSON minimal) : celui-ci nourrit la vue
 * Thymeleaf avec les éléments visuels et le formulaire RSVP. Aucune donnée administrative
 * interne (id, invitationCode, organisation...) n'est exposée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicInvitationPage {

    private String token;
    private String guestFirstName;
    private String guestLastName;

    private String weddingDisplayName;
    private String couplePhotoUrl;
    private String groomPhotoUrl;
    private String bridePhotoUrl;
    private String message;

    // Événement principal (date / heure / lieu) affiché sur la carte.
    private String eventName;
    private String eventDate;        // formaté en français
    private String eventStartTime;   // formaté "HH'h'mm"
    private String eventVenue;

    // Block RSVP : réponse courante + capacité max.
    private String status;           // statut invitation (GENERATED/SENT...)
    private boolean canRespond;      // false si invitation annulée/expirée
    private String rsvpStatus;       // ACCEPTED / DECLINED / null
    private Integer rsvpNumberOfAttendees;
    private int maxAccepted;         // maximum = 1 + allowedCompanions
}