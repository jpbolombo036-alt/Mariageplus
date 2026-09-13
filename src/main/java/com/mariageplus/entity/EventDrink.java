package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Déclaration de DISPONIBILITÉ d'une boisson du catalogue pour un événement.
 * Créée/modifiée par l'ORGANISATEUR : les invités ne voient que les boissons
 * du catalogue déclarées disponibles pour leur événement.
 */
@Entity
@Table(name = "event_drinks", indexes = {
        @Index(name = "idx_event_drinks_wedding", columnList = "wedding_id"),
        @Index(name = "idx_event_drinks_catalog", columnList = "catalog_item_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDrink extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(name = "catalog_item_id", nullable = false)
    private Long catalogItemId;

    /** Boisson disponible pour les invités de cet événement. */
    @Column(nullable = false)
    private boolean available = false;

    @Column(name = "display_order")
    private Integer displayOrder;
}
