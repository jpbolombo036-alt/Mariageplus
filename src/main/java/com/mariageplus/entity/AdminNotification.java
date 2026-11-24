package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Notification in-app destinée aux SUPER_ADMIN (cloche de l'en-tête).
 * Partagée entre tous les SUPER_ADMIN : la lecture par l'un la marque lue pour tous.
 */
@Entity
@Table(name = "admin_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminNotification extends BaseEntity {

    public static final String TYPE_ORGANIZATION_REGISTERED = "ORGANIZATION_REGISTERED";
    public static final String TYPE_CONTACT_MESSAGE = "CONTACT_MESSAGE";
    public static final String TYPE_PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String message;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sender_email")
    private String senderEmail;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
