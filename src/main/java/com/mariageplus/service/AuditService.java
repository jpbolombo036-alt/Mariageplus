package com.mariageplus.service;

import com.mariageplus.entity.AuditLog;
import com.mariageplus.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Trace les actions importantes (création de mariage, envoi d'invitation,
 * check-in, désactivation d'utilisateur, ...).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(String action, Long entityId, String entityType,
                       Long userId, Long organizationId, String details) {
        AuditLog logEntry = AuditLog.builder()
                .action(action)
                .entityId(entityId)
                .entityType(entityType)
                .userId(userId)
                .organizationId(organizationId)
                .details(details)
                .performedAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(logEntry);
    }
}