package com.mariageplus.dto.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Réponse d'une session (sous-étape) d'un événement.
 */
@Data
@Builder
public class EventSessionResponse {
    private Long id;
    private Long eventId;
    private String name;
    private String type;
    private String description;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venueName;
    private String venueAddress;
    private String city;
    private String commune;
    private String country;
    private Double latitude;
    private Double longitude;
    private String mapUrl;
    private Integer displayOrder;
    private Boolean active;
}
