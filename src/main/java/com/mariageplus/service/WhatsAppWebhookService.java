package com.mariageplus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.NotificationLog;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * Réception des webhooks Meta (champ "messages" de la Cloud API WhatsApp) :
 * les statuts de livraison (delivered / read / failed) sont rattachés au
 * journal d'envoi via l'identifiant de message (wamid) stocké à l'envoi.
 *
 * Sécurité : handshake GET par {@code WHATSAPP_WEBHOOK_VERIFY_TOKEN} et
 * vérification de la signature {@code X-Hub-Signature-256} (HMAC-SHA256 du
 * corps brut avec {@code WHATSAPP_APP_SECRET}) quand le secret est configuré.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookService {

    private final NotificationLogRepository logRepository;
    private final InvitationRepository invitationRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.whatsapp.webhook-verify-token:}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    /**
     * Handshake de vérification Meta : renvoie le challenge si le jeton configuré
     * correspond, sinon null (le contrôleur répondra 403).
     */
    public String verifySubscription(String mode, String token, String challenge) {
        if (!StringUtils.hasText(verifyToken)
                || !"subscribe".equals(mode)
                || !verifyToken.equals(token)
                || !StringUtils.hasText(challenge)) {
            return null;
        }
        return challenge;
    }

    /**
     * Vérifie la signature X-Hub-Signature-256 (HMAC-SHA256 du corps brut avec le
     * secret d'app). Si le secret n'est pas configuré, le contrôle est désactivé.
     */
    public boolean isSignatureValid(String signatureHeader, String rawBody) {
        if (!StringUtils.hasText(appSecret)) {
            return true;
        }
        if (!StringUtils.hasText(signatureHeader) || rawBody == null) {
            return false;
        }
        String prefix = "sha256=";
        if (!signatureHeader.toLowerCase().startsWith(prefix)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * digest.length);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.UTF_8),
                    signatureHeader.substring(prefix.length())
                            .toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Webhook WhatsApp : erreur de vérification de signature", ex);
            return false;
        }
    }

    /**
     * Traite un payload de webhook : met à jour le journal d'envoi
     * (SENT → DELIVERED → READ, ou FAILED avec la raison Meta). Toujours
     * silencieux : un payload inconnu ou illisible ne lève jamais d'exception
     * (sinon Meta retenterait indéfiniment).
     */
    public void processStatuses(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode status : change.path("value").path("statuses")) {
                        handleStatus(status);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Webhook WhatsApp : payload illisible (ignoré) : {}", ex.getMessage());
        }
    }

    private void handleStatus(JsonNode status) {
        String messageId = status.path("id").asText(null);
        String state = status.path("status").asText(null);
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(state)) {
            return;
        }
        NotificationLog notificationLog = logRepository
                .findFirstByMessageIdOrderByIdDesc(messageId)
                .orElse(null);
        if (notificationLog == null) {
            // Message non envoyé par cette plateforme (ou antérieur au suivi).
            log.debug("Webhook WhatsApp : aucun log pour {}", messageId);
            return;
        }
        switch (state) {
            case "delivered" -> markDelivered(notificationLog);
            case "read" -> markRead(notificationLog);
            case "failed" -> markFailed(notificationLog, status.path("errors"));
            case "sent", "deleted" -> { /* déjà géré côté envoi */ }
            default -> log.debug("Webhook WhatsApp : statut '{}' ignoré", state);
        }
    }

    private void markDelivered(NotificationLog notificationLog) {
        if ("FAILED".equals(notificationLog.getStatus())
                || "DELIVERED".equals(notificationLog.getStatus())
                || "READ".equals(notificationLog.getStatus())) {
            return; // jamais de rétrogradation
        }
        notificationLog.setStatus("DELIVERED");
        logRepository.save(notificationLog);
        touchDeliveredAt(notificationLog.getInvitationId());
    }

    private void markRead(NotificationLog notificationLog) {
        if ("FAILED".equals(notificationLog.getStatus())
                || "READ".equals(notificationLog.getStatus())) {
            return;
        }
        notificationLog.setStatus("READ");
        logRepository.save(notificationLog);
        touchDeliveredAt(notificationLog.getInvitationId());
    }

    private void markFailed(NotificationLog notificationLog, JsonNode errors) {
        if ("FAILED".equals(notificationLog.getStatus())) {
            return;
        }
        String detail = null;
        if (errors.isArray() && errors.size() > 0) {
            JsonNode error = errors.get(0);
            int code = error.path("code").asInt(0);
            String message = error.path("message").asText(null);
            String title = error.path("title").asText(null);
            String text = (message != null && !message.isBlank()) ? message : title;
            detail = (code > 0 ? "[" + code + "] " : "") + text;
        }
        notificationLog.setStatus("FAILED");
        notificationLog.setErrorMessage(detail != null ? abbreviate(detail) : "Échec de livraison (webhook Meta)");
        logRepository.save(notificationLog);
    }

    /** Pose deliveredAt sur l'invitation (idempotent). */
    private void touchDeliveredAt(Long invitationId) {
        if (invitationId == null) {
            return;
        }
        invitationRepository.findById(invitationId).ifPresent(invitation -> {
            if (invitation.getDeliveredAt() == null) {
                invitation.setDeliveredAt(LocalDateTime.now());
                invitationRepository.save(invitation);
            }
        });
    }

    private String abbreviate(String message) {
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }
}