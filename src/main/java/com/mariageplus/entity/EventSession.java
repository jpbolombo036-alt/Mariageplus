package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Session (sous-étape) d'un événement : cérémonie civile, religieuse,
 * réception, after party... Remplace l'ancienne entité {@link WeddingEvent}
 * et s'applique à tous les types d'événements ({@link Event}).
 * Les dates / heures et le lieu sont validés côté service.
 */
@Entity
@Table(name = "event_sessions", indexes = {
        @Index(name = "idx_event_sessions_event", columnList = "event_id"),
        @Index(name = "idx_event_sessions_event_date", columnList = "event_id, session_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSession extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventSessionType type;

    @Column(length = 1000)
    private String description;

    @Column(name = "session_date")
    private LocalDate sessionDate;

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
