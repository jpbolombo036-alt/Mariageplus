package com.mariageplus.service;

import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.dto.rsvp.PublicRsvpResponse;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Wedding;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.WeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final WeddingRepository weddingRepository;

    /**
     * Lecture publique : retourne les infos minimales + le RSVP courant éventuel.
     */
    public PublicInvitationResponse getPublicInvitation(String publicToken) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId()).orElse(null);
        Wedding wedding = weddingRepository.findById(invitation.getWeddingId()).orElse(null);
        Rsvp rsvp = rsvpRepository.findByInvitationId(invitation.getId()).orElse(null);

        return PublicInvitationResponse.builder()
                .guestFirstName(guest != null ? guest.getFirstName() : null)
                .guestLastName(guest != null ? guest.getLastName() : null)
                .weddingDisplayName(wedding != null ? wedding.getDisplayName() : null)
                .status(invitation.getStatus().name())
                .rsvpStatus(rsvp != null ? rsvp.getStatus().name() : null)
                .rsvpNumberOfAttendees(rsvp != null ? rsvp.getNumberOfAttendees() : null)
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
        rsvp.setRespondedAt(LocalDateTime.now());
        Rsvp saved = rsvpRepository.save(rsvp);

        return PublicRsvpResponse.builder()
                .invitationStatus(invitation.getStatus().name())
                .rsvpStatus(saved.getStatus().name())
                .numberOfAttendees(saved.getNumberOfAttendees())
                .respondedAt(saved.getRespondedAt())
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
}
