package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Catégorie d'invités d'un mariage (Famille du marié, Amis, VIP, ...).
 * Appartient obligatoirement à un mariage ({@code weddingId}) dont l'organisation
 * contrôle l'accès.
 */
@Entity
@Table(name = "guest_categories", indexes = {
        @Index(name = "idx_guest_categories_wedding", columnList = "wedding_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestCategory extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
