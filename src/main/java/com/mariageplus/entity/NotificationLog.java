package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Journal d'une notification envoyée (ou tentée) dans le cadre d'un envoi en
 * masse : une ligne par invitation ciblée. Permet le rapport d'échecs
 * (numéro absent/invalide, erreur API Meta, ...) et une relance ciblée.
 */
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notif_logs_batch", columnList = "batch_id"),
        @Index(name = "idx_notif_logs_invitation", columnList = "invitation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog extends BaseEntity {

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(name = "invitation_id", nullable = false)
    private Long invitationId;

    @Column(name = "guest_id")
    private Long guestId;

    /** Canal : WHATSAPP (V1). */
    @Column(nullable = false, length = 20)
    private String channel;

    /** SENT / FAILED / SKIPPED. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Raison de l'échec ou du saut (téléphone absent, erreur API...). */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
