package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Prestation ajoutée à une réservation (sono, décoration, nettoyage…), dans la devise de la réservation. */
@Entity
@Table(name = "venue_booking_extras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueBookingExtra extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(nullable = false, length = 150)
    private String label;

    @Builder.Default
    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
