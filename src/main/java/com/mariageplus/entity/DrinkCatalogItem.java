package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Boisson du CATALOGUE GLOBAL (créée par le SUPER_ADMIN uniquement).
 * Les organisateurs ne créent plus de boissons : ils déclarent disponibles
 * les boissons de ce catalogue pour leurs événements (pivot {@link EventDrink}).
 */
@Entity
@Table(name = "drink_catalog", indexes = {
        @Index(name = "idx_drink_catalog_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrinkCatalogItem extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    /** Clé S3 de la photo (si le stockage objet est actif). */
    @Column(name = "image_key", length = 500)
    private String imageKey;

    /** Photo en fallback (base de données) quand le stockage objet n'est pas configuré. */
    @Column
    private byte[] image;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
