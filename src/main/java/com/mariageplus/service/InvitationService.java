package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.checkin.QrCodeResponse;
import com.mariageplus.dto.invitation.CreateInvitationRequest;
import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.dto.invitation.SendInvitationResponse;
import com.mariageplus.dto.invitation.UpdateInvitationRequest;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.InvitationMapper;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.security.SecureTokens;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module invitations.
 *
 * - invitationCode : identifiant métier unique, non prévisible, généré backend.
 * - publicToken : jeton public aléatoire (SecureRandom), unique, séparé de l'id
 *   et du code ; jamais exposé dans les réponses administratives.
 * - Règle métier : un invité ne peut avoir qu'une invitation active (non
 *   supprimée) ; l'invité doit appartenir au mariage du périmètre.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final InvitationMapper invitationMapper;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final QrCodeService qrCodeService;
    private final InvitationMailService invitationMailService;

    @org.springframework.beans.factory.annotation.Value("${app.invitation.max-reminders:3}")
    private int maxReminders;

    @Transactional
    public InvitationResponse create(Long weddingId, CreateInvitationRequest request) {
        securityUtils.assertPermission("INVITATION_CREATE");
        Event event = eventService.loadInOrgScope(weddingId);

        Guest guest = guestRepository.findByIdAndWeddingId(request.getGuestId(), weddingId)
                .orElseThrow(() -> new IllegalArgumentException("L'invité n'appartient pas à ce mariage"));

        if (invitationRepository.existsByGuestId(request.getGuestId())) {
            throw new ConflictException("Une invitation existe déjà pour cet invité");
        }

        Invitation invitation = Invitation.builder()
                .weddingId(weddingId)
                .guestId(request.getGuestId())
                .invitationCode(uniqueCode())
                .publicToken(uniqueToken())
                .status(InvitationStatus.GENERATED)
                .build();
        Invitation saved = invitationRepository.save(invitation);
        auditService.record("INVITATION_CREATE", saved.getId(), "Invitation",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création d'une invitation pour l'invité " + guest.getFirstName() + " " + guest.getLastName());
        return invitationMapper.toResponse(saved);
    }

    public InvitationResponse getById(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_VIEW");
        eventService.loadInOrgScope(weddingId);
        return invitationMapper.toResponse(loadInvitation(weddingId, invitationId));
    }

    public PageResponse<InvitationResponse> list(Long weddingId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("INVITATION_VIEW");
        eventService.loadInOrgScope(weddingId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Invitation> invitationPage = invitationRepository.findActiveByWeddingId(weddingId, pageable);
        List<InvitationResponse> content = invitationPage.getContent().stream()
                .map(invitationMapper::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, invitationPage);
    }

    /**
     * Liste des invitations envoyées mais non répondues (relance manuelle assistée).
     */
    public List<InvitationResponse> listNonResponders(Long weddingId) {
        securityUtils.assertPermission("INVITATION_VIEW");
        eventService.loadInOrgScope(weddingId);
        return invitationRepository.findNonRespondersByWeddingId(weddingId).stream()
                .map(invitationMapper::toResponse).collect(Collectors.toList());
    }

    /** Nombre d'invitations envoyées non répondues (état bien + relance). */
    public long countNonResponders(Long weddingId) {
        securityUtils.assertPermission("INVITATION_VIEW");
        eventService.loadInOrgScope(weddingId);
        return invitationRepository.countNonRespondersByWeddingId(weddingId);
    }
    @Transactional
    public InvitationResponse update(Long weddingId, Long invitationId, UpdateInvitationRequest request) {
        securityUtils.assertPermission("INVITATION_UPDATE");
        Event wedding = eventService.loadInOrgScope(weddingId);
        Invitation invitation = loadInvitation(weddingId, invitationId);

        if (request.getStatus() != null) {
            invitation.setStatus(parseStatus(request.getStatus()));
        }
        if (request.getSentAt() != null) invitation.setSentAt(request.getSentAt());
        if (request.getLastSentAt() != null) invitation.setLastSentAt(request.getLastSentAt());

        Invitation saved = invitationRepository.save(invitation);
        auditService.record("INVITATION_UPDATE", saved.getId(), "Invitation",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(), "Modification d'une invitation");
        return invitationMapper.toResponse(saved);
    }

    /**
     * Suppression logique : l'invitation disparaît des listes administratives et
     * son publicToken ne permet plus de la retrouver (filtre soft-delete).
     */
    @Transactional
    public void delete(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_DELETE");
        eventService.loadInOrgScope(weddingId);
        Invitation invitation = loadInvitation(weddingId, invitationId);
        invitation.softDelete();
        invitationRepository.save(invitation);
    }

    @Transactional
    public SendInvitationResponse send(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_SEND");
        return dispatch(weddingId, invitationId, false);
    }

    @Transactional
    public SendInvitationResponse resend(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_RESEND");
        return dispatch(weddingId, invitationId, true);
    }

    @Transactional
    public InvitationResponse cancel(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_CANCEL");
        Event wedding = eventService.loadInOrgScope(weddingId);
        Invitation invitation = loadInvitation(weddingId, invitationId);
        if (invitation.getStatus() == InvitationStatus.CANCELLED
                || invitation.getStatus() == InvitationStatus.EXPIRED) {
            throw new ConflictException("Cette invitation ne peut plus être annulée");
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
        Invitation saved = invitationRepository.save(invitation);
        auditService.record("INVITATION_CANCEL", saved.getId(), "Invitation",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(), "Annulation d'une invitation");
        return invitationMapper.toResponse(saved);
    }

    private SendInvitationResponse dispatch(Long weddingId, Long invitationId, boolean resend) {
        Event wedding = eventService.loadInOrgScope(weddingId);
        Invitation invitation = loadInvitation(weddingId, invitationId);
        Guest guest = guestRepository.findByIdAndWeddingId(invitation.getGuestId(), weddingId)
                .orElseThrow(() -> new ResourceNotFoundException("Invité introuvable"));

        if (!StringUtils.hasText(guest.getEmail())) {
            throw new IllegalArgumentException("L'invité n'a pas d'adresse email");
        }

        InvitationStatus status = invitation.getStatus();
        if (resend) {
            if (status != InvitationStatus.SENT) {
                throw new ConflictException("Le renvoi n'est possible que pour une invitation déjà envoyée");
            }
            if (invitation.getReminderCount() >= maxReminders) {
                throw new ConflictException("Limite de relances atteinte (" + maxReminders + ")");
            }
        } else if (status == InvitationStatus.SENT) {
            throw new ConflictException("L'invitation a déjà été envoyée : utilisez le renvoi");
        } else if (status != InvitationStatus.GENERATED && status != InvitationStatus.DRAFT) {
            throw new ConflictException("Cette invitation ne peut pas être envoyée (statut " + status + ")");
        }

        String url = invitationMailService.publicInviteUrl(invitation.getPublicToken());
        boolean emailSent = invitationMailService.sendInvitation(guest, wedding, url);

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getSentAt() == null) {
            invitation.setSentAt(now);
        }
        invitation.setLastSentAt(now);
        if (resend) {
            invitation.setReminderCount(invitation.getReminderCount() + 1);
        }
        invitation.setStatus(InvitationStatus.SENT);
        Invitation saved = invitationRepository.save(invitation);
        String action = resend ? "INVITATION_RESEND" : "INVITATION_SEND";
        auditService.record(action, saved.getId(), "Invitation",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(),
                (resend ? "Renvoi" : "Envoi") + " de l'invitation à " + guest.getEmail());
        return SendInvitationResponse.builder()
                .status(saved.getStatus().name())
                .sentAt(saved.getSentAt())
                .lastSentAt(saved.getLastSentAt())
                .emailSent(emailSent)
                .publicInviteUrl(url)
                .build();
    }

    /**
     * Résout une invitation par son publicToken avec les règles d'accès public :
     * token inexistant, invitation supprimée (soft-delete), CANCELLED, EXPIRED,
     * ou mariage dont l'événement principal est passé → 404.
     */
    public Invitation resolvePublicInvitation(String publicToken) {
        Invitation invitation = invitationRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));
        if (invitation.isDeleted()
                || invitation.getStatus() == InvitationStatus.CANCELLED
                || invitation.getStatus() == InvitationStatus.EXPIRED) {
            throw new ResourceNotFoundException("Invitation introuvable");
        }
        // Expiration temporelle (4a) : uniquement si un événement principal existe.
        // S'il y a un événement avec une date et que cette date est passée → lien fermé (404).
        // S'il n'y a aucun événement, on ne ferme pas (comportement legacy).
        if (invitation.getStatus() == InvitationStatus.GENERATED
                || invitation.getStatus() == InvitationStatus.SENT) {
            eventRepository.findById(invitation.getWeddingId())
                    .filter(event -> event.getEventDate() != null)
                    .filter(event -> event.getEventDate().isBefore(LocalDate.now()))
                    .ifPresent(expired -> {
                        throw new ResourceNotFoundException("Invitation introuvable");
                    });
        }
        return invitation;
    }

    /**
     * Trace la première ouverture du lien public (suivi organisateur). Idempotent :
     * ne met à jour que si {@code openedAt} n'est pas déjà posé.
     */
    @Transactional
    public void markOpened(String publicToken) {
        Invitation invitation = resolvePublicInvitation(publicToken);
        if (invitation.getOpenedAt() == null) {
            invitation.setOpenedAt(LocalDateTime.now());
            invitationRepository.save(invitation);
        }
    }

    /**
     * Accès public par publicToken : retourne uniquement des données minimales.
     */
    public PublicInvitationResponse findPublicByToken(String publicToken) {
        Invitation invitation = resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));

        return PublicInvitationResponse.builder()
                .guestFirstName(guest.getFirstName())
                .guestLastName(guest.getLastName())
                .status(invitation.getStatus().name())
                .build();
    }

    /**
     * QR code d'une invitation : généré à partir du publicToken (stable), renvoyé
     * sous forme de data URI PNG. Le token brut n'est pas exposé. Permission
     * INVITATION_VIEW + périmètre organisationnel requis.
     */
    public QrCodeResponse getQrData(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_VIEW");
        eventService.loadInOrgScope(weddingId);
        Invitation invitation = loadInvitation(weddingId, invitationId);
        return new QrCodeResponse(qrCodeService.generateQrDataUri(invitation.getPublicToken()));
    }

    /**
     * Rotation du QR : remplace le {@code publicToken} par un nouveau jeton
     * aléatoire, ce qui invalide immédiatement tous les QR/liens publics déjà
     * émis (fuite, erreur d'envoi). L'invitation, son RSVP et ses affectations de
     * table sont conservés. Le nouveau QR (data URI PNG) est renvoyé pour
     * réimpression/renvoi direct. L'invitation est verrouillée (PESSIMISTIC_WRITE)
     * pour sérialiser d'éventuelles rotations concurrentes.
     */
    @Transactional
    public QrCodeResponse rotateQrToken(Long weddingId, Long invitationId) {
        securityUtils.assertPermission("INVITATION_UPDATE");
        Event wedding = eventService.loadInOrgScope(weddingId);
        Invitation invitation = invitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> notFound(invitationId, weddingId));
        if (invitation.isDeleted() || !invitation.getWeddingId().equals(weddingId)) {
            throw notFound(invitationId, weddingId);
        }
        if (invitation.getStatus() == InvitationStatus.CANCELLED
                || invitation.getStatus() == InvitationStatus.EXPIRED) {
            throw notFound(invitationId, weddingId);
        }
        invitation.setPublicToken(uniqueToken());
        Invitation saved = invitationRepository.save(invitation);
        auditService.record("INVITATION_QR_ROTATE", saved.getId(), "Invitation",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(),
                "Rotation du QR : ancien token révoqué");
        return new QrCodeResponse(qrCodeService.generateQrDataUri(saved.getPublicToken()));
    }

    private static ResourceNotFoundException notFound(Long invitationId, Long weddingId) {
        return new ResourceNotFoundException(
                "Invitation non trouvée avec l'ID: " + invitationId + " pour le mariage " + weddingId);
    }

    private Invitation loadInvitation(Long weddingId, Long invitationId) {
        Invitation invitation = invitationRepository.findByIdAndWeddingId(invitationId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invitation non trouvée avec l'ID: " + invitationId + " pour le mariage " + weddingId));
        if (invitation.isDeleted()) {
            throw new ResourceNotFoundException(
                    "Invitation non trouvée avec l'ID: " + invitationId + " pour le mariage " + weddingId);
        }
        return invitation;
    }

    private String uniqueCode() {
        String code;
        do {
            code = "INV-" + SecureTokens.randomCode(8);
        } while (invitationRepository.existsByInvitationCode(code));
        return code;
    }

    private String uniqueToken() {
        String token;
        do {
            token = SecureTokens.randomToken(32);
        } while (invitationRepository.existsByPublicToken(token));
        return token;
    }

    private InvitationStatus parseStatus(String raw) {
        try {
            return InvitationStatus.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Statut d'invitation invalide : " + raw);
        }
    }
}