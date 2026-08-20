package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Événement d'un mariage (cérémonie, réception, after party, ...).
 * Appartient à un mariage ({@code weddingId}) dont l'organisation contrôle l'accès.
 * Les dates / heures et le lieu sont validés côté service.
 */
@Entity
@Table(name = "wedding_events", indexes = {
        @Index(name = "idx_wedding_events_wedding", columnList = "wedding_id"),
        @Index(name = "idx_wedding_events_wedding_date", columnList = "wedding_id, event_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeddingEvent extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WeddingEventType type;

    @Column(length = 1000)
    private String description;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

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

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
