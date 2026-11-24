package com.mariageplus.dto.notification;

import java.util.List;

public record AdminNotificationListResponse(long unreadCount, List<AdminNotificationResponse> items) {
}
