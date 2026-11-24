package com.mariageplus.dto.notification;

import com.mariageplus.entity.UserNotification;

import java.time.LocalDateTime;
import java.util.List;

public record UserNotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        boolean broadcast,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static UserNotificationResponse from(UserNotification n) {
        return new UserNotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.isBroadcast(), n.getCreatedAt(), n.getReadAt());
    }

    public record ListResponse(long unreadCount, List<UserNotificationResponse> items) {
    }
}
