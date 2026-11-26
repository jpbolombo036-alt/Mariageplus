package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Taux de change USD → CDF saisi par une salle. Historique en ajout seul :
 * le taux en vigueur est le plus récent ; un paiement garde le taux qu'il a
 * réellement utilisé, même si le taux change ensuite.
 */
@Entity
@Table(name = "venue_exchange_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** Francs congolais pour 1 dollar américain. */
    @Column(name = "cdf_per_usd", nullable = false, precision = 14, scale = 4)
    private BigDecimal cdfPerUsd;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "set_by")
    private Long setBy;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (effectiveAt == null) {
            effectiveAt = createdAt;
        }
    }
}
