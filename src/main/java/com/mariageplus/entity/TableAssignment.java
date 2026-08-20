package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Affectation d'un guest à une table (Étape 8).
 *
 * <p>Un guest ne peut avoir qu'une seule affectation active
 * ({@code UNIQUE(guest_id)}), garantie en base. weddingId/guest.wedding ne sont
 * pas dupliqués : ils sont obtenus via {@code TableAssignment → WeddingTable →
 * Wedding}. Le guest et la table doivent appartenir au même mariage (vérifié
 * côté service). {@code assignedBy} trace l'utilisateur.
 */
@Entity
@Table(name = "table_assignments", indexes = {
        @Index(name = "idx_table_assignments_table", columnList = "wedding_table_id"),
        @Index(name = "idx_table_assignments_guest", columnList = "guest_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_table_assignments_guest", columnNames = {"guest_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableAssignment extends BaseEntity {

    @Column(name = "wedding_table_id", nullable = false)
    private Long weddingTableId;

    @Column(name = "guest_id", nullable = false)
    private Long guestId;

    @Column(name = "assigned_by")
    private Long assignedBy;
}