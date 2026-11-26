package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Tarif d'une salle (ex : Journée, Soirée, Week-end). */
@Entity
@Table(name = "venue_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueRate extends BaseEntity {

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private VenueCurrency currency;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
