package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dépense de salle (sortie d'argent). Montant toujours positif, dans sa propre
 * devise. {@code venueId} nul : dépense générale de l'organisation.
 */
@Entity
// Redéclaré ici : le @SQLRestriction de BaseEntity (@MappedSuperclass) n'est pas hérité.
@SQLRestriction("deleted_at IS NULL")
@Table(name = "venue_expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueExpense extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "venue_id")
    private Long venueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VenueExpenseCategory category;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private VenueCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VenuePaymentMethod method;

    /** Référence (n° de facture, transaction M-Pesa…). */
    @Column(length = 100)
    private String reference;

    @Column(name = "spent_at", nullable = false)
    private LocalDateTime spentAt;

    @Column(length = 500)
    private String note;

    @Column(name = "recorded_by")
    private Long recordedBy;
}
