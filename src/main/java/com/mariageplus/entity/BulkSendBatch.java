package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un envoi en masse d'invitations pour un mariage (canal WHATSAPP en V1).
 * Déclenché par l'organisateur ("envoyer à tous" / par catégorie / relance des
 * non-répondants) ; traité de façon asynchrone par {@code BulkSendWorker}.
 *
 * Les compteurs sont mis à jour au fil de l'eau pour permettre au front
 * d'afficher la progression (sent / failed / skipped sur total).
 */
@Entity
@Table(name = "bulk_send_batches", indexes = {
        @Index(name = "idx_bulk_batches_wedding", columnList = "wedding_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkSendBatch extends BaseEntity {

    /** Identifiant de l'événement (racine métier "weddingId" historique). */
    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(name = "organization_id")
    private Long organizationId;

    /** Canal d'envoi : WHATSAPP (V1). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "WHATSAPP";

    /** PENDING → IN_PROGRESS → COMPLETED (ou FAILED si erreur globale). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_count", nullable = false)
    @Builder.Default
    private int totalCount = 0;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private int sentCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private int skippedCount = 0;

    @Column(name = "created_by")
    private Long createdBy;
}
