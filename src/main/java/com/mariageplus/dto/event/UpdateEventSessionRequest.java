package com.mariageplus.dto.event;

import com.mariageplus.entity.EventSessionType;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Modification partielle d'une session (champs null = inchangés).
 */
@Data
public class UpdateEventSessionRequest {

    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    private EventSessionType type;

    @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
    private String description;

    private LocalDate sessionDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Size(max = 200, message = "Le nom du lieu ne doit pas dépasser 200 caractères")
    private String venueName;

    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
    private String venueAddress;

    @Size(max = 100, message = "La ville ne doit pas dépasser 100 caractères")
    private String city;

    @Size(max = 100, message = "La commune ne doit pas dépasser 100 caractères")
    private String commune;

    @Size(max = 100, message = "Le pays ne doit pas dépasser 100 caractères")
    private String country;

    private Double latitude;

    private Double longitude;

    @Size(max = 1000, message = "L'URL de la carte ne doit pas dépasser 1000 caractères")
    private String mapUrl;

    private Integer displayOrder;

    private Boolean active;
}
