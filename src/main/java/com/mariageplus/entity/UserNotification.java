package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Notification in-app d'un utilisateur (cloche des organisateurs).
 * Une ligne par destinataire : chacun a son propre état de lecture.
 */
@Entity
@Table(name = "user_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification extends BaseEntity {

    public static final String TYPE_ADMIN_MESSAGE = "ADMIN_MESSAGE";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String message;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    @Builder.Default
    private boolean broadcast = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
