package com.mariageplus.dto.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Réponse d'un événement : socle commun + {@code weddingDetails} si le type
 * est WEDDING (sinon null) + les sessions (sous-étapes) de l'événement.
 */
@Data
@Builder
public class EventResponse {
    private Long id;
    private String name;
    private String type;
    private String description;
    private String message;
    private LocalDate eventDate;
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
    private String status;
    /** Codes des couleurs demandées aux invités (max 3), dans l'ordre saisi. */
    private java.util.List<String> dressColors;
    /** Une photo du pagne / tissu à porter est-elle jointe à l'événement ? */
    private boolean hasDressImage;
    private Integer displayOrder;
    private Boolean active;
    private Long organizationId;
    private Boolean hasImage;
    private WeddingDetailsResponse weddingDetails;
    private List<EventSessionResponse> sessions;
}
