package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Invité d'un mariage. Appartient obligatoirement à un mariage ({@code weddingId}).
 * Peut être rattaché à une catégorie du MÊME mariage ({@code categoryId}).
 * {@code allowedCompanions} = nombre d'accompagnants autorisés (total autorisé =
 * 1 + allowedCompanions). La logique Invitation/RSVP n'est pas traitée à cette étape.
 */
@Entity
@Table(name = "guests", indexes = {
        @Index(name = "idx_guests_wedding", columnList = "wedding_id"),
        @Index(name = "idx_guests_category", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guest extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(name = "allowed_companions")
    private Integer allowedCompanions;

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
