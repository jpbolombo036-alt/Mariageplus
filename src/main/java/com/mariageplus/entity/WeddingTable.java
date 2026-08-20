package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Table d'un mariage (Étape 8). Appartient obligatoirement à un {@link Wedding}
 * ({@code weddingId}). Le nom est unique dans le même mariage (même nom autorisé
 * dans un autre mariage). La capacité est mesurée en nombre de guests affectés.
 */
@Entity
@Table(name = "wedding_tables", indexes = {
        @Index(name = "idx_wedding_tables_wedding", columnList = "wedding_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_wedding_tables_name", columnNames = {"wedding_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeddingTable extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Integer capacity;

    @Column(length = 500)
    private String description;
}