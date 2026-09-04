package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.bulksend.BulkSendBatchResponse;
import com.mariageplus.dto.bulksend.BulkSendRequest;
import com.mariageplus.dto.bulksend.NotificationLogResponse;
import com.mariageplus.entity.BulkSendBatch;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.NotificationLog;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.BulkSendBatchRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.NotificationLogRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestration de l'envoi en masse d'invitations (WhatsApp en V1).
 *
 * Le démarrage est synchrone (validation, sélection, création du batch) puis
 * le traitement est délégué à {@link BulkSendWorker} (asynchrone, cadencé) :
 * la requête HTTP répond immédiatement avec le batch à suivre
 * (GET .../send-bulk/{batchId}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkSendService {

    private final EventService eventService;
    private final InvitationRepository invitationRepository;
    private final GuestRepository guestRepository;
    private final BulkSendBatchRepository batchRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final SecurityUtils securityUtils;
    private final WhatsAppService whatsAppService;
    private final BulkSendWorker worker;

    @Value("${app.invitation.max-reminders:3}")
    private int maxReminders;

    @Value("${app.whatsapp.public-api-base-url:}")
    private String publicApiBaseUrl;

    @Value("${storage.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    private static final int DEFAULT_LOG_PAGE_SIZE = 50;

    @Transactional
    public BulkSendBatchResponse startBulkSend(Long weddingId, BulkSendRequest request) {
        securityUtils.assertPermission("INVITATION_SEND");
        Event event = eventService.loadInOrgScope(weddingId);
        securityUtils.assertWeddingAccess(weddingId);

        if (request == null || request.getChannel() == null
                || !"WHATSAPP".equalsIgnoreCase(request.getChannel())) {
            throw new IllegalArgumentException("Canal non supporté : seul WHATSAPP est disponible");
        }
        if (!whatsAppService.isConfigured()) {
            throw new IllegalArgumentException(
                    "WhatsApp non configuré : définissez WHATSAPP_TOKEN et WHATSAPP_PHONE_NUMBER_ID");
        }

        List<Invitation> invitations = selectInvitations(weddingId, request);
        if (invitations.isEmpty()) {
            throw new IllegalArgumentException("Aucune invitation à envoyer pour ce critère");
        }

        BulkSendBatch batch = batchRepository.save(BulkSendBatch.builder()
                .weddingId(weddingId)
                .organizationId(event.getOrganizationId())
                .channel("WHATSAPP")
                .status("PENDING")
                .totalCount(invitations.size())
                .createdBy(securityUtils.getCurrentUserId())
                .build());

        List<Long> invitationIds = invitations.stream().map(Invitation::getId).toList();
        String imageUrl = resolveEventImageUrl(event);
        worker.processBatch(batch.getId(), weddingId, invitationIds, imageUrl,
                request.isResend() || request.isOnlyPendingRsvp());

        log.info("Envoi en masse démarré : batch={}, mariage={}, cibles={}",
                batch.getId(), weddingId, invitationIds.size());
        return toResponse(batch);
    }

    @Transactional(readOnly = true)
    public BulkSendBatchResponse getBatch(Long weddingId, Long batchId) {
        securityUtils.assertPermission("INVITATION_VIEW");
        eventService.loadInOrgScope(weddingId);
        securityUtils.assertWeddingAccess(weddingId);
        BulkSendBatch batch = batchRepository.findByIdAndWeddingId(batchId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException("Envoi en masse introuvable"));
        return toResponse(batch);
    }

    /** Journal du batch : résultat par invitation (valide d'abord le périmètre). */
    @Transactional(readOnly = true)
    public PageResponse<NotificationLogResponse> getLogs(Long weddingId, Long batchId, int page, int size) {
        getBatch(weddingId, batchId); // permission + périmètre + existence
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<NotificationLog> logs = notificationLogRepository.findByBatchIdOrderByIdAsc(batchId, pageable);
        List<NotificationLogResponse> content = logs.getContent().stream()
                .map(log -> NotificationLogResponse.builder()
                        .id(log.getId())
                        .invitationId(log.getInvitationId())
                        .guestId(log.getGuestId())
                        .channel(log.getChannel())
                        .status(log.getStatus())
                        .errorMessage(log.getErrorMessage())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
        return PageResponse.of(content, logs);
    }

    /** Sélection des invitations selon le mode et les filtres de la requête. */
    private List<Invitation> selectInvitations(Long weddingId, BulkSendRequest request) {
        List<Invitation> candidates;
        if (request.isOnlyPendingRsvp()) {
            candidates = invitationRepository.findNonRespondersByWeddingId(weddingId);
        } else {
            candidates = invitationRepository.findByWeddingId(weddingId);
        }

        Set<Long> guestIdsInCategory = request.getCategoryId() == null ? null
                : guestRepository.findByWeddingIdAndCategoryId(weddingId, request.getCategoryId())
                        .stream().map(Guest::getId).collect(Collectors.toSet());

        boolean resend = request.isResend() || request.isOnlyPendingRsvp();
        List<Invitation> result = new ArrayList<>();
        for (Invitation invitation : candidates) {
            InvitationStatus status = invitation.getStatus();
            if (status == InvitationStatus.CANCELLED || status == InvitationStatus.EXPIRED) {
                continue;
            }
            if (resend) {
                if (status != InvitationStatus.SENT) {
                    continue;
                }
                if (invitation.getReminderCount() >= maxReminders) {
                    continue;
                }
            } else if (status != InvitationStatus.GENERATED && status != InvitationStatus.DRAFT) {
                continue;
            }
            if (guestIdsInCategory != null
                    && (invitation.getGuestId() == null || !guestIdsInCategory.contains(invitation.getGuestId()))) {
                continue;
            }
            if (request.getInvitationIds() != null && !request.getInvitationIds().isEmpty()
                    && !request.getInvitationIds().contains(invitation.getId())) {
                continue;
            }
            result.add(invitation);
        }
        result.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        return result;
    }

    /**
     * URL publique de la photo de couverture pour l'en-tête du template :
     * priorité au CDN S3 (S3_PUBLIC_BASE_URL + clé), sinon l'endpoint public
     * de l'API (PUBLIC_API_BASE_URL + /api/events/{id}/image). Null si aucune
     * URL publique n'est configurable → template envoyé sans en-tête image.
     */
    private String resolveEventImageUrl(Event event) {
        if (event.getImageKey() != null && !event.getImageKey().isBlank()
                && s3PublicBaseUrl != null && !s3PublicBaseUrl.isBlank()) {
            String base = s3PublicBaseUrl.trim();
            return base.endsWith("/") ? base + event.getImageKey() : base + "/" + event.getImageKey();
        }
        if (publicApiBaseUrl != null && !publicApiBaseUrl.isBlank()) {
            String base = publicApiBaseUrl.trim();
            String path = "/api/events/" + event.getId() + "/image";
            return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
        }
        return null;
    }

    private BulkSendBatchResponse toResponse(BulkSendBatch batch) {
        return BulkSendBatchResponse.builder()
                .id(batch.getId())
                .weddingId(batch.getWeddingId())
                .channel(batch.getChannel())
                .status(batch.getStatus())
                .totalCount(batch.getTotalCount())
                .sentCount(batch.getSentCount())
                .failedCount(batch.getFailedCount())
                .skippedCount(batch.getSkippedCount())
                .createdAt(batch.getCreatedAt())
                .build();
    }
}
