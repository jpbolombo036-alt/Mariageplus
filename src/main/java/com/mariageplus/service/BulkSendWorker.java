package com.mariageplus.service;

import com.mariageplus.entity.BulkSendBatch;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.NotificationLog;
import com.mariageplus.exception.WhatsAppDeliveryException;
import com.mariageplus.repository.BulkSendBatchRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.NotificationLogRepository;
import com.mariageplus.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Traitement asynchrone d'un envoi en masse : une invitation à la fois,
 * cadencé ({@code app.whatsapp.bulk.delay-ms}) pour respecter les limites de
 * débit de l'API WhatsApp. Les compteurs du batch et le journal
 * ({@link NotificationLog}) sont mis à jour au fil de l'eau : le front suit
 * la progression sans attendre la fin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkSendWorker {

    private final BulkSendBatchRepository batchRepository;
    private final InvitationRepository invitationRepository;
    private final GuestRepository guestRepository;
    private final NotificationLogRepository logRepository;
    private final EventRepository eventRepository;
    private final WhatsAppService whatsAppService;
    private final InvitationMailService invitationMailService;
    private final AuditService auditService;

    @Value("${app.whatsapp.bulk.delay-ms:2000}")
    private long delayMs;

    @Value("${app.whatsapp.default-country-code:}")
    private String defaultCountryCode;

    /**
     * Traite le batch : s'exécute sur l'exécuteur dédié "bulkSendExecutor"
     * (un seul worker → cadencement et ordre garantis).
     */
    @Async("bulkSendExecutor")
    public void processBatch(Long batchId, Long weddingId, List<Long> invitationIds,
                             String imageUrl, boolean resend) {
        BulkSendBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            log.error("Batch d'envoi en masse introuvable : {}", batchId);
            return;
        }
        Event event = eventRepository.findById(weddingId).orElse(null);
        if (event == null) {
            finish(batch, "FAILED", "Événement introuvable");
            return;
        }

        batch.setStatus("IN_PROGRESS");
        batch.setSentCount(0);
        batch.setFailedCount(0);
        batch.setSkippedCount(0);
        batchRepository.save(batch);

        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (Long invitationId : invitationIds) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("Traitement du batch {} interrompu", batchId);
                    finish(batch, "FAILED", "Traitement interrompu");
                    return;
                }
            }

            Invitation invitation = invitationRepository.findById(invitationId).orElse(null);
            if (invitation == null) {
                skipped++;
                logRepository.save(log(batch, null, null, "SKIPPED", "Invitation introuvable"));
                batch = saveCounters(batch, sent, failed, skipped);
                continue;
            }
            Guest guest = invitation.getGuestId() == null ? null
                    : guestRepository.findById(invitation.getGuestId()).orElse(null);
            String whatsAppId = guest == null ? null
                    : PhoneNormalizer.toWhatsAppId(PhoneNormalizer.toE164(guest.getPhone(), defaultCountryCode));
            if (whatsAppId == null) {
                skipped++;
                logRepository.save(log(batch, invitation, guest, "SKIPPED",
                        "Téléphone absent ou invalide"));
                batch = saveCounters(batch, sent, failed, skipped);
                continue;
            }

            String url = invitationMailService.publicInviteUrl(invitation.getPublicToken());
            try {
                whatsAppService.sendInvitationTemplate(whatsAppId, guest, event, url, imageUrl);
                markInvitationSent(invitation, resend);
                sent++;
                logRepository.save(log(batch, invitation, guest, "SENT", null));
            } catch (WhatsAppDeliveryException ex) {
                failed++;
                logRepository.save(log(batch, invitation, guest, "FAILED", abbreviate(ex.getMessage())));
            } catch (Exception ex) {
                failed++;
                log.error("Erreur inattendue d'envoi WhatsApp (invitation {})", invitationId, ex);
                logRepository.save(log(batch, invitation, guest, "FAILED", "Erreur inattendue"));
            }
            batch = saveCounters(batch, sent, failed, skipped);
        }

        finish(batch, "COMPLETED", null);
    }

    /** Passage de l'invitation à SENT (même sémantique que l'envoi unitaire). */
    private void markInvitationSent(Invitation invitation, boolean resend) {
        LocalDateTime now = LocalDateTime.now();
        if (invitation.getSentAt() == null) {
            invitation.setSentAt(now);
        }
        invitation.setLastSentAt(now);
        if (resend) {
            invitation.setReminderCount(invitation.getReminderCount() + 1);
        }
        invitation.setStatus(InvitationStatus.SENT);
        invitationRepository.save(invitation);
    }

    private BulkSendBatch saveCounters(BulkSendBatch batch, int sent, int failed, int skipped) {
        batch.setSentCount(sent);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        return batchRepository.save(batch);
    }

    private void finish(BulkSendBatch batch, String status, String failureReason) {
        batch.setStatus(status);
        BulkSendBatch saved = batchRepository.save(batch);
        if ("FAILED".equals(status) && failureReason != null) {
            log.error("Batch {} terminé en échec : {}", saved.getId(), failureReason);
        }
        auditService.record("INVITATION_BULK_SEND", saved.getId(), "BulkSendBatch",
                saved.getCreatedBy(), saved.getOrganizationId(),
                "Envoi en masse " + saved.getChannel() + " — total=" + saved.getTotalCount()
                        + " envoyés=" + saved.getSentCount()
                        + " échecs=" + saved.getFailedCount()
                        + " ignorés=" + saved.getSkippedCount()
                        + (failureReason != null ? " (" + failureReason + ")" : ""));
    }

    private NotificationLog log(BulkSendBatch batch, Invitation invitation, Guest guest,
                                String status, String errorMessage) {
        return NotificationLog.builder()
                .batchId(batch.getId())
                .weddingId(batch.getWeddingId())
                .invitationId(invitation == null ? null : invitation.getId())
                .guestId(guest == null ? null : guest.getId())
                .channel(batch.getChannel())
                .status(status)
                .errorMessage(abbreviate(errorMessage))
                .build();
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }
}
