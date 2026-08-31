package com.mariageplus.service;

import com.mariageplus.dto.invitation.PublicInvitationPage;
import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.dto.rsvp.PublicRsvpResponse;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.WeddingDetails;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.WeddingDetailsRepository;
import com.mariageplus.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Module RSVP public.
 *
 * La seule clé publique est le publicToken (chemin de l'URL) : il détermine
 * l'invitation côté backend (via {@link InvitationService#resolvePublicInvitation}).
 * Le RSVP est rattaché à l'invitation (donc indirectement au guest et au mariage
 * corrects) et une seule réponse courante existe par invitation (idempotence).
 */
@Service
@RequiredArgsConstructor
public class RsvpService {

    private final RsvpRepository rsvpRepository;
    private final InvitationService invitationService;
    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final WeddingDetailsRepository weddingDetailsRepository;

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FR =
            DateTimeFormatter.ofPattern("HH'h'mm");

    /**
     * Lecture publique pour la page web invité : marque l'ouverture (suivi), puis
     * construit toutes les données nécessaires à la vue ({@code /invitations/{token}}).
     */
    @Transactional
    public PublicInvitationPage getPublicPage(String publicToken) {
        invitationService.markOpened(publicToken);
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId()).orElse(null);
        Event event = eventRepository.findById(invitation.getWeddingId()).orElse(null);
        Rsvp rsvp = rsvpRepository.findByInvitationId(invitation.getId()).orElse(null);
        WeddingDetails details = weddingDetailsRepository.findByEventId(invitation.getWeddingId()).orElse(null);
        String displayName = event != null ? event.getName()
                : (details != null ? details.getDisplayName() : null);

        boolean canRespond = invitation.getStatus() != com.mariageplus.entity.InvitationStatus.CANCELLED
                && invitation.getStatus() != com.mariageplus.entity.InvitationStatus.EXPIRED;

        return PublicInvitationPage.builder()
                .token(publicToken)
                .guestFirstName(guest != null ? guest.getFirstName() : null)
                .guestLastName(guest != null ? guest.getLastName() : null)
                .weddingDisplayName(displayName)
                .couplePhotoUrl(photoUrl(invitation.getWeddingId(), details != null ? details.getCouplePhotoUrl() : null, "couple"))
                .groomPhotoUrl(photoUrl(invitation.getWeddingId(), details != null ? details.getGroomPhotoUrl() : null, "groom"))
                .bridePhotoUrl(photoUrl(invitation.getWeddingId(), details != null ? details.getBridePhotoUrl() : null, "bride"))
                .message(event != null ? event.getMessage() : null)
                .eventName(event != null ? event.getName() : null)
                .eventDate(event != null && event.getEventDate() != null ? DATE_FR.format(event.getEventDate()) : null)
                .eventStartTime(event != null && event.getStartTime() != null ? TIME_FR.format(event.getStartTime()) : null)
                .eventVenue(event != null ? venueText(event) : null)
                .status(invitation.getStatus().name())
                .canRespond(canRespond)
                .rsvpStatus(rsvp != null ? rsvp.getStatus().name() : null)
                .rsvpNumberOfAttendees(rsvp != null ? rsvp.getNumberOfAttendees() : null)
                .maxAccepted(guest != null ? maximumAllowed(guest) : 1)
                .build();
    }

    /**
     * Résout l'URL affichable d'une photo : URL externe telle quelle,
     * clé S3 → endpoint public de streaming, sinon null.
     */
    private String photoUrl(Long eventId, String stored, String kind) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            return stored;
        }
        return "/api/events/" + eventId + "/photos/" + kind;
    }

    private String venueText(Event event) {
        StringBuilder sb = new StringBuilder();
        if (event.getVenueName() != null) sb.append(event.getVenueName());
        if (event.getVenueAddress() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(event.getVenueAddress());
        }
        if (event.getCity() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(event.getCity());
        }
        return sb.toString();
    }

    /**
     * Lecture publique (JSON) : retourne les infos minimales + le RSVP courant + les
     * éléments visuels / date-lieu / message / capacité. Ne marque PAS l'ouverture
     * (goal : idempotent, utilisé par l'app Flutter invitée).
     */
    public PublicInvitationResponse getPublicInvitation(String publicToken) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId()).orElse(null);
        Event event = eventRepository.findById(invitation.getWeddingId()).orElse(null);
        Rsvp rsvp = rsvpRepository.findByInvitationId(invitation.getId()).orElse(null);
        WeddingDetails details = weddingDetailsRepository.findByEventId(invitation.getWeddingId()).orElse(null);
        String displayName = event != null ? event.getName()
                : (details != null ? details.getDisplayName() : null);

        return PublicInvitationResponse.builder()
                .guestFirstName(guest != null ? guest.getFirstName() : null)
                .guestLastName(guest != null ? guest.getLastName() : null)
                .weddingDisplayName(displayName)
                .couplePhotoUrl(photoUrl(invitation.getWeddingId(), details != null ? details.getCouplePhotoUrl() : null, "couple"))
                .groomPhotoUrl(photoUrl(invitation.getWeddingId(), details != null ? details.getGroomPhotoUrl() : null, "groom"))
                .bridePhotoUrl(photoUrl(invitation.getWeddingId(), details != null ? details.getBridePhotoUrl() : null, "bride"))
                .message(event != null ? event.getMessage() : null)
                .eventName(event != null ? event.getName() : null)
                .eventDate(event != null && event.getEventDate() != null
                        ? DATE_FR.format(event.getEventDate()) : null)
                .eventStartTime(event != null && event.getStartTime() != null
                        ? TIME_FR.format(event.getStartTime()) : null)
                .eventVenue(event != null ? venueText(event) : null)
                .maxAccepted(guest != null ? maximumAllowed(guest) : 1)
                .status(invitation.getStatus().name())
                .rsvpStatus(rsvp != null ? rsvp.getStatus().name() : null)
                .rsvpNumberOfAttendees(rsvp != null ? rsvp.getNumberOfAttendees() : null)
                .rsvpDrinkChoice(rsvp != null ? rsvp.getDrinkChoice() : null)
                .publicToken(invitation.getPublicToken())
                .build();
    }

    /**
     * Soumission publique : crée la réponse si absente, sinon met à jour la réponse
     * courante (ACCEPTED → DECLINED et inversement). Idempotent (une seule réponse
     * par invitation, {@code UNIQUE(invitation_id)}).
     *
     * <p>Le nombre maximal de participants est toujours résolu côté backend à partir
     * du guest lié à l'invitation ({@code maximumAllowed = 1 + allowedCompanions}),
     * jamais fourni par le client. Règles :
     * <ul>
     *   <li>ACCEPTED → {@code 1 <= numberOfAttendees <= maximumAllowed}</li>
     *   <li>DECLINED → {@code numberOfAttendees == 0} (une valeur &gt; 0 est refusée)</li>
     * </ul>
     */
    @Transactional
    public PublicRsvpResponse submitRsvp(String publicToken, SubmitRsvpRequest request) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId())
                .orElseThrow(() -> new ResourceNotFoundException("Invité introuvable"));
        RsvpStatus status = parseSubmission(request.getStatus());
        int attendees = parseAttendees(request.getNumberOfAttendees());
        int maximum = maximumAllowed(guest);
        validate(status, attendees, maximum);

        Rsvp rsvp = rsvpRepository.findByInvitationId(invitation.getId())
                .orElseGet(() -> Rsvp.builder().invitationId(invitation.getId()).build());
        rsvp.setStatus(status);
        rsvp.setNumberOfAttendees(attendees);
        rsvp.setDrinkChoice(request.getDrinkChoice());
        rsvp.setRespondedAt(LocalDateTime.now());
        Rsvp saved = rsvpRepository.save(rsvp);

        return PublicRsvpResponse.builder()
                .invitationStatus(invitation.getStatus().name())
                .rsvpStatus(saved.getStatus().name())
                .numberOfAttendees(saved.getNumberOfAttendees())
                .respondedAt(saved.getRespondedAt())
                .drinkChoice(saved.getDrinkChoice())
                .build();
    }

    /** Maximum autorisé, déterminé exclusivement côté backend : 1 + acompanants. */
    private int maximumAllowed(Guest guest) {
        int companions = guest.getAllowedCompanions() == null ? 0 : guest.getAllowedCompanions();
        return 1 + companions;
    }

    /**
     * Validation métier de la cohérence statut / nombre de participants.
     * {@code DECLINED} avec un nombre &gt; 0 est refusé (aucun silencieux).
     */
    private void validate(RsvpStatus status, int attendees, int maximum) {
        if (status == RsvpStatus.DECLINED) {
            if (attendees != 0) {
                throw new IllegalArgumentException(
                        "Une réponse DECLINED doit avoir numberOfAttendees = 0 (reçu : " + attendees + ")");
            }
            return;
        }
        if (attendees < 1) {
            throw new IllegalArgumentException(
                    "Nombre de participants invalide pour ACCEPTED (minimum 1, reçu : " + attendees + ")");
        }
        if (attendees > maximum) {
            throw new IllegalArgumentException(
                    "Le nombre de participants (" + attendees + ") dépasse le maximum autorisé (" + maximum + ")");
        }
    }

    private int parseAttendees(Integer raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Le nombre de participants est requis");
        }
        if (raw < 0) {
            throw new IllegalArgumentException("Le nombre de participants ne peut pas être négatif");
        }
        return raw;
    }

    /**
     * La soumission n'accepte que ACCEPTED ou DECLINED ; toute autre valeur → 400.
     */
    private RsvpStatus parseSubmission(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La réponse est requise");
        }
        String value = raw.trim().toUpperCase();
        if (value.equals("ACCEPTED") || value.equals("DECLINED")) {
            return RsvpStatus.valueOf(value);
        }
        throw new IllegalArgumentException("Réponse invalide : " + raw);
    }

    public Long resolveEventId(String publicToken) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        return invitation.getWeddingId();
    }
}
