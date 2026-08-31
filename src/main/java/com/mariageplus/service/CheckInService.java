package com.mariageplus.service;

import com.mariageplus.dto.checkin.CheckInListItemResponse;
import com.mariageplus.dto.checkin.CheckInRequest;
import com.mariageplus.dto.checkin.CheckInResponse;
import com.mariageplus.dto.checkin.CheckInScanResponse;
import com.mariageplus.dto.checkin.ScanCheckInRequest;
import com.mariageplus.entity.CheckIn;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module check-in (Étape 7). Trois valeurs distinctes, jamais confondues :
 * maximumAllowed = 1 + allowedCompanions (capacité), expectedAttendees =
 * RSVP.numberOfAttendees si ACCEPTED sinon 0, checkedInAttendees =
 * SUM(check_in.number_of_attendees). Règle d'accès : invitation active + RSVP
 * ACCEPTED + numberOfAttendees &gt; 0, dans la limite du RSVP.
 *
 * <p>Concurrence : check-in transactionnel, invitation chargée avec
 * PESSIMISTIC_WRITE (findByPublicTokenForUpdate), somme recalculée puis
 * vérification puis insertion dans la même transaction — les doubles check-ins
 * concurrents sont sérialisés par le verrou sur l'invitation.</p>
 *
 * <p>Isolation : autorisation = permission + organisation (résolue via
 * Invitation → Wedding, jamais via un weddingId fourni par le frontend).</p>
 */
@Service
@RequiredArgsConstructor
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final InvitationRepository invitationRepository;
    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final RsvpRepository rsvpRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** Scan : résout le QR et retourne l'état (sans inscription). */
    public CheckInScanResponse scan(ScanCheckInRequest request) {
        securityUtils.assertPermission("CHECKIN_SCAN");
        Invitation invitation = resolveActiveInvitation(request.getQrToken());
        assertSameWedding(invitation, request.getWeddingId());
        Event event = loadWedding(invitation);
        securityUtils.assertWeddingAccess(event.getId());
        securityUtils.assertOrganizationAccess(event.getOrganizationId());
        Rsvp rsvp = loadRsvp(invitation);
        int checkedIn = checkInRepository.sumByInvitationId(invitation.getId());
        int expected = expectedAttendees(rsvp);
        int remaining = Math.max(0, expected - checkedIn);
        boolean canCheckIn = rsvp != null
                && rsvp.getStatus() == RsvpStatus.ACCEPTED
                && expected > 0
                && remaining > 0;
        return toScanResponse(invitation, event, rsvp, expected, checkedIn, remaining, canCheckIn);
    }

    /** Check-in : enregistre une entrée dans la limite du RSVP (verrou pessimiste). */
    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        securityUtils.assertPermission("CHECKIN_CREATE");
        int attendees = parseAttendees(request.getNumberOfAttendees());

        Invitation invitation = invitationRepository.findByPublicTokenForUpdate(request.getQrToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));
        assertActive(invitation);
        assertSameWedding(invitation, request.getWeddingId());

        Event event = loadWedding(invitation);
        securityUtils.assertWeddingAccess(event.getId());
        securityUtils.assertOrganizationAccess(event.getOrganizationId());

        Rsvp rsvp = loadRsvp(invitation);
        int expected = expectedAttendeesChecked(rsvp);
        int checkedIn = checkInRepository.sumByInvitationId(invitation.getId());
        if (checkedIn + attendees > expected) {
            throw new ConflictException(
                    "Dépassement de capacité : " + checkedIn + " déjà entré(s), "
                            + attendees + " demandé(s), attendu " + expected);
        }

        CheckIn saved = checkInRepository.save(CheckIn.builder()
                .invitationId(invitation.getId())
                .numberOfAttendees(attendees)
                .checkedInAt(LocalDateTime.now())
                .checkedInBy(securityUtils.getCurrentUserId())
                .build());

        auditService.record("CHECKIN_CREATE", saved.getId(), "CheckIn",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Check-in de " + attendees + " personne(s) — invitation " + invitation.getId());

        int total = checkedIn + attendees;
        int remaining = Math.max(0, expected - total);
        return toCheckInResponse(invitation, event, rsvp, saved, attendees, expected, total, remaining);
    }

    /**
     * Annule un check-in (suppression logique). La somme des présences
     * redescend : un nouveau scan voit les places recréditées.
     */
    @Transactional
    public void cancel(Long checkInId) {
        securityUtils.assertPermission("CHECKIN_CANCEL");
        CheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in introuvable"));

        Invitation invitation = invitationRepository.findByIdForUpdate(checkIn.getInvitationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));
        Event event = loadWedding(invitation);
        securityUtils.assertWeddingAccess(event.getId());
        securityUtils.assertOrganizationAccess(event.getOrganizationId());

        checkIn.softDelete();
        checkInRepository.save(checkIn);
        auditService.record("CHECKIN_CANCEL", checkIn.getId(), "CheckIn",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Annulation du check-in (" + checkIn.getNumberOfAttendees() + " personne(s))");
    }

    /** RSVP attendu pour le check-in : exige un ACCEPTED avec un nombre &gt; 0. */
    private int expectedAttendeesChecked(Rsvp rsvp) {
        if (rsvp == null) {
            throw new ConflictException("Aucune réponse RSVP pour cette invitation");
        }
        if (rsvp.getStatus() != RsvpStatus.ACCEPTED) {
            throw new ConflictException("Invitation non confirmée (RSVP " + rsvp.getStatus() + ")");
        }
        int expected = rsvp.getNumberOfAttendees() == null ? 0 : rsvp.getNumberOfAttendees();
        if (expected <= 0) {
            throw new ConflictException("Nombre de participants confirmé invalide");
        }
        return expected;
    }

    /** RSVP attendu pour le scan (0 si non confirmé/absent). */
    private int expectedAttendees(Rsvp rsvp) {
        if (rsvp == null || rsvp.getStatus() != RsvpStatus.ACCEPTED) {
            return 0;
        }
        return rsvp.getNumberOfAttendees() == null ? 0 : rsvp.getNumberOfAttendees();
    }

    private Invitation resolveActiveInvitation(String qrToken) {
        Invitation invitation = invitationRepository.findByPublicToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));
        assertActive(invitation);
        return invitation;
    }

    private void assertSameWedding(Invitation invitation, Long requestedWeddingId) {
        if (requestedWeddingId == null || !invitation.getWeddingId().equals(requestedWeddingId)) {
            throw new ResourceNotFoundException("Invitation introuvable");
        }
    }

    private void assertActive(Invitation invitation) {
        if (invitation.isDeleted()
                || invitation.getStatus() == InvitationStatus.CANCELLED
                || invitation.getStatus() == InvitationStatus.EXPIRED) {
            throw new ResourceNotFoundException("Invitation introuvable");
        }
    }

    private Event loadWedding(Invitation invitation) {
        return eventRepository.findById(invitation.getWeddingId())
                .orElseThrow(() -> new ResourceNotFoundException("Mariage introuvable"));
    }

    private Rsvp loadRsvp(Invitation invitation) {
        return rsvpRepository.findByInvitationId(invitation.getId()).orElse(null);
    }

    private int parseAttendees(Integer raw) {
        if (raw == null || raw <= 0) {
            throw new IllegalArgumentException("Le nombre de personnes doit être au moins 1");
        }
        return raw;
    }

    private String guestName(Invitation invitation) {
        Guest guest = guestRepository.findById(invitation.getGuestId()).orElse(null);
        if (guest == null || (guest.getFirstName() == null && guest.getLastName() == null)) {
            return "Inconnu";
        }
        return (guest.getFirstName() == null ? "" : guest.getFirstName())
                + " " + (guest.getLastName() == null ? "" : guest.getLastName());
    }

    private CheckInScanResponse toScanResponse(Invitation invitation, Event wedding, Rsvp rsvp,
                                               int expected, int checkedIn, int remaining, boolean canCheckIn) {
        return CheckInScanResponse.builder()
                .guestName(guestName(invitation))
                .weddingDisplayName(wedding.getName())
                .invitationStatus(invitation.getStatus().name())
                .rsvpStatus(rsvp == null ? null : rsvp.getStatus().name())
                .expectedAttendees(expected)
                .checkedInAttendees(checkedIn)
                .remainingAttendees(remaining)
                .canCheckIn(canCheckIn)
                .tableName(tableName(invitation))
                .drinkChoice(rsvp == null ? null : rsvp.getDrinkChoice())
                .build();
    }

    private CheckInResponse toCheckInResponse(Invitation invitation, Event wedding, Rsvp rsvp,
                                              CheckIn saved, int attendees, int expected,
                                              int total, int remaining) {
        return CheckInResponse.builder()
                .checkInId(saved.getId())
                .guestName(guestName(invitation))
                .weddingDisplayName(wedding.getName())
                .invitationStatus(invitation.getStatus().name())
                .rsvpStatus(rsvp == null ? null : rsvp.getStatus().name())
                .numberOfAttendees(attendees)
                .checkedInAt(saved.getCheckedInAt())
                .expectedAttendees(expected)
                .checkedInAttendees(total)
                .remainingAttendees(remaining)
                .tableName(tableName(invitation))
                .drinkChoice(rsvp == null ? null : rsvp.getDrinkChoice())
                .build();
    }

    private String tableName(Invitation invitation) {
        return tableAssignmentRepository.findTableNameByGuestId(invitation.getGuestId()).orElse(null);
    }

    /**
     * Liste des invités présents dans la salle pour un événement : agrégat par
     * invitation (somme des entrées, dernier horodatage) avec nom, table et
     * boisson choisie au RSVP. Isolation : permission + accès événement/organisation.
     */
    @Transactional(readOnly = true)
    public List<CheckInListItemResponse> listPresent(Long weddingId) {
        securityUtils.assertPermission("CHECKIN_SCAN");
        Event event = eventRepository.findById(weddingId)
                .orElseThrow(() -> new ResourceNotFoundException("Mariage introuvable"));
        securityUtils.assertWeddingAccess(event.getId());
        securityUtils.assertOrganizationAccess(event.getOrganizationId());

        Map<Long, List<CheckIn>> byInvitation = checkInRepository
                .findByWeddingIdOrderByCheckedInAtDesc(weddingId).stream()
                .collect(Collectors.groupingBy(CheckIn::getInvitationId));

        List<CheckInListItemResponse> items = new ArrayList<>();
        for (Map.Entry<Long, List<CheckIn>> e : byInvitation.entrySet()) {
            Invitation invitation = invitationRepository.findById(e.getKey()).orElse(null);
            if (invitation == null) {
                continue;
            }
            List<CheckIn> entries = e.getValue();
            int attendees = entries.stream().mapToInt(CheckIn::getNumberOfAttendees).sum();
            LocalDateTime lastAt = entries.stream()
                    .map(CheckIn::getCheckedInAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            Rsvp rsvp = loadRsvp(invitation);
            items.add(CheckInListItemResponse.builder()
                    .invitationId(invitation.getId())
                    .guestId(invitation.getGuestId())
                    .guestName(guestName(invitation))
                    .numberOfAttendees(attendees)
                    .lastCheckedInAt(lastAt)
                    .tableName(tableName(invitation))
                    .drinkChoice(rsvp == null ? null : rsvp.getDrinkChoice())
                    .build());
        }
        items.sort(Comparator.comparing(CheckInListItemResponse::getLastCheckedInAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }
}