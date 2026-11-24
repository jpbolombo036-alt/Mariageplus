package com.mariageplus.dto.notification;

import com.mariageplus.entity.AdminNotification;

import java.time.LocalDateTime;

public record AdminNotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        Long organizationId,
        Long userId,
        String senderEmail,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static AdminNotificationResponse from(AdminNotification n) {
        return new AdminNotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getOrganizationId(), n.getUserId(), n.getSenderEmail(), n.getCreatedAt(), n.getReadAt());
    }
}
