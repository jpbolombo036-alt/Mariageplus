package com.mariageplus.service;

import com.mariageplus.dto.notification.AdminMessageRequest;
import com.mariageplus.dto.notification.UserNotificationResponse;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.User;
import com.mariageplus.entity.UserNotification;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.OrganizationRepository;
import com.mariageplus.repository.UserNotificationRepository;
import com.mariageplus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cloche des utilisateurs : messages du SUPER_ADMIN vers une organisation ou vers
 * tous les organisateurs. Une notification est créée par destinataire.
 */
@Service
@RequiredArgsConstructor
public class UserNotificationService {

    static final String ORGANIZER_ROLE = "ORGANISATEUR";
    static final int LIST_SIZE = 30;

    private final UserNotificationRepository repository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** @return le nombre d'organisateurs destinataires. */
    @Transactional
    public int sendAdminMessage(Long senderId, AdminMessageRequest request) {
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (title.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Le titre et le message sont requis");
        }

        boolean broadcast = request.getAudience() == AdminMessageRequest.Audience.ALL_ORGANIZERS;
        Long organizationId = broadcast ? null : request.getOrganizationId();
        Set<Long> recipients = new LinkedHashSet<>();
        if (broadcast) {
            userRepository.findActiveByRoleCode(ORGANIZER_ROLE).stream().map(User::getId).forEach(recipients::add);
        } else {
            if (organizationId == null) {
                throw new IllegalArgumentException("L'organisation destinataire est requise");
            }
            if (!organizationRepository.existsById(organizationId)) {
                throw new ResourceNotFoundException("Organisation introuvable");
            }
            organizationMemberRepository.findActiveByOrganizationAndRole(organizationId, ORGANIZER_ROLE).stream()
                    .map(OrganizationMember::getUser).map(User::getId).forEach(recipients::add);
        }
        recipients.remove(senderId);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(broadcast
                    ? "Aucun organisateur actif sur la plateforme"
                    : "Aucun organisateur actif dans cette organisation");
        }

        List<UserNotification> notifications = recipients.stream().map(userId -> UserNotification.builder()
                .userId(userId)
                .type(UserNotification.TYPE_ADMIN_MESSAGE)
                .title(title)
                .message(message)
                .senderUserId(senderId)
                .organizationId(organizationId)
                .broadcast(broadcast)
                .build()).toList();
        repository.saveAll(notifications);

        auditService.record("ADMIN_MESSAGE_SENT", organizationId, broadcast ? "Platform" : "Organization",
                senderId, organizationId,
                (broadcast ? "Message à tous les organisateurs" : "Message à l'organisation")
                        + " (" + recipients.size() + " destinataire(s)) : " + title);
        return recipients.size();
    }

    @Transactional(readOnly = true)
    public UserNotificationResponse.ListResponse list(Long userId) {
        var items = repository.findByUserId(userId,
                        PageRequest.of(0, LIST_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .stream().map(UserNotificationResponse::from).toList();
        return new UserNotificationResponse.ListResponse(repository.countByUserIdAndReadAtIsNull(userId), items);
    }

    /** 404 aussi pour la notification d'un autre utilisateur : son existence n'est pas révélée. */
    @Transactional
    public void markRead(Long userId, Long id) {
        UserNotification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            repository.save(notification);
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, LocalDateTime.now());
    }
}
