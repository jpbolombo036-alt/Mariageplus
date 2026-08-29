package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Événement : nouvelle racine métier unifiée (Phase 1 — coexistence avec
 * {@link Wedding}). Tous les types (mariage, collation, anniversaire...)
 * partagent ce socle commun ; seul le type WEDDING dispose d'une fiche de
 * détails dédiée ({@link WeddingDetails}).
 *
 * L'isolation est portée par {@code organizationId} et vérifiée côté service
 * via {@code OrganizationMember}. {@code createdAt}/{@code updatedAt}/
 * {@code deletedAt} sont hérités de {@link BaseEntity}.
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_org", columnList = "organization_id"),
        @Index(name = "idx_events_org_date", columnList = "organization_id, event_date"),
        @Index(name = "idx_events_org_type", columnList = "organization_id, type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Column(length = 2000)
    private String description;

    /** Petit mot personnalisé de l'organisateur, affiché dans l'email et la page invité. */
    @Column(length = 2000)
    private String message;

    @Column(name = "event_date")
    private java.time.LocalDate eventDate;

    @Column(name = "start_time")
    private java.time.LocalTime startTime;

    @Column(name = "end_time")
    private java.time.LocalTime endTime;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(name = "venue_address", length = 255)
    private String venueAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String commune;

    @Column(length = 100)
    private String country;

    private Double latitude;

    private Double longitude;

    @Column(name = "map_url", length = 1000)
    private String mapUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
