package com.mariageplus.service;

import com.mariageplus.dto.notification.AdminNotificationListResponse;
import com.mariageplus.dto.notification.AdminNotificationResponse;
import com.mariageplus.entity.AdminNotification;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.User;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.exception.TooManyRequestsException;
import com.mariageplus.repository.AdminNotificationRepository;
import com.mariageplus.repository.OrganizationRepository;
import com.mariageplus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Notifications in-app des SUPER_ADMIN (cloche) : inscriptions d'organisations et
 * messages des organisateurs. Ne dépend pas du SMTP.
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    static final int LIST_SIZE = 30;
    static final Duration CONTACT_COOLDOWN = Duration.ofMinutes(10);

    private final AdminNotificationRepository repository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditService auditService;

    @Transactional
    public void notifyOrganizationRegistered(Long organizationId, String organizationName,
                                             Long userId, String creatorName, String creatorEmail) {
        repository.save(AdminNotification.builder()
                .type(AdminNotification.TYPE_ORGANIZATION_REGISTERED)
                .title(truncate("Nouvelle organisation à activer : " + organizationName, 255))
                .message(truncate(creatorName + " (" + creatorEmail + ") vient de créer un compte. "
                        + "Activez la création d'événements et WhatsApp dans Console → Organisations.", 2000))
                .organizationId(organizationId)
                .userId(userId)
                .senderEmail(creatorEmail)
                .build());
    }

    /**
     * Message libre d'un utilisateur vers l'équipe de la plateforme.
     * Limité à un message toutes les {@link #CONTACT_COOLDOWN} par utilisateur.
     */
    @Transactional
    public void sendContactMessage(Long userId, Long organizationId, String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Le message est requis");
        }
        if (repository.existsByTypeAndUserIdAndCreatedAtAfter(AdminNotification.TYPE_CONTACT_MESSAGE, userId,
                LocalDateTime.now().minus(CONTACT_COOLDOWN))) {
            throw new TooManyRequestsException(
                    "Votre message précédent a bien été reçu. Vous pourrez en envoyer un autre dans quelques minutes.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        String organizationName = organizationId == null ? null
                : organizationRepository.findById(organizationId).map(Organization::getName).orElse(null);
        String author = user.getFirstName() + " " + user.getLastName();

        repository.save(AdminNotification.builder()
                .type(AdminNotification.TYPE_CONTACT_MESSAGE)
                .title(truncate("Message de " + author
                        + (organizationName != null ? " — " + organizationName : ""), 255))
                .message(truncate(text, 2000))
                .organizationId(organizationId)
                .userId(userId)
                .senderEmail(user.getEmail())
                .build());
        auditService.record("CONTACT_MESSAGE_SENT", userId, "User", userId, organizationId,
                "Message envoyé à l'équipe de la plateforme");
    }

    @Transactional(readOnly = true)
    public AdminNotificationListResponse list() {
        var items = repository.findAllBy(PageRequest.of(0, LIST_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .stream().map(AdminNotificationResponse::from).toList();
        return new AdminNotificationListResponse(repository.countByReadAtIsNull(), items);
    }

    @Transactional
    public void markRead(Long id) {
        AdminNotification notification = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            repository.save(notification);
        }
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead(LocalDateTime.now());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
