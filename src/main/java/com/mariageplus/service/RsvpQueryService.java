package com.mariageplus.service;

import com.mariageplus.dto.guest.RsvpSummaryResponse;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ADDITIF (GESTIONNAIRE_INVITES) : lecture des réponses RSVP d'un mariage.
 *
 * Sécurité identique aux autres modules : permission granulaire puis périmètre
 * organisation via {@link WeddingService#loadInOrgScope(Long)}. Aucune écriture.
 */
@Service
@RequiredArgsConstructor
public class RsvpQueryService {

    private final RsvpRepository rsvpRepository;
    private final InvitationRepository invitationRepository;
    private final SecurityUtils securityUtils;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<RsvpSummaryResponse> listForWedding(Long weddingId) {
        securityUtils.assertPermission("GUEST_VIEW");
        eventService.loadInOrgScope(weddingId);

        var invitations = invitationRepository.findByWeddingId(weddingId);
        if (invitations.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> guestByInvitation = invitations.stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> i.getGuestId(),
                        (a, b) -> a));

        List<Long> invitationIds = invitations.stream()
                .map(i -> i.getId())
                .toList();
        List<Rsvp> rsvps = rsvpRepository.findByInvitationIdIn(invitationIds);

        return rsvps.stream()
                .map(r -> RsvpSummaryResponse.builder()
                        .invitationId(r.getInvitationId())
                        .guestId(guestByInvitation.get(r.getInvitationId()))
                        .status(r.getStatus() == null ? null : r.getStatus().name())
                        .numberOfAttendees(r.getNumberOfAttendees())
                        .respondedAt(r.getRespondedAt())
                        .build())
                .toList();
    }
}