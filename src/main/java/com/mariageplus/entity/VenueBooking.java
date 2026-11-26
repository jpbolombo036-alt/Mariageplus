package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Réservation d'une salle par un client. */
@Entity
// Redéclaré ici : le @SQLRestriction de BaseEntity (@MappedSuperclass) n'est pas hérité.
@SQLRestriction("deleted_at IS NULL")
@Table(name = "venue_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueBooking extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30)
    private EventType eventType;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "guest_count")
    private Integer guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VenueBookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private VenueCurrency currency;

    @Column(name = "rate_id")
    private Long rateId;

    @Builder.Default
    @Column(name = "base_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    /** Prix de base + prestations - remise (recalculé à chaque changement). */
    @Builder.Default
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Échéance d'une OPTION sans acompte : au-delà, la date est libérée. */
    @Column(name = "option_expires_at")
    private LocalDateTime optionExpiresAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** Événement MariagePlus créé depuis cette réservation (invitations, RSVP…). */
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "created_by")
    private Long createdBy;
}
