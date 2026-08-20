package com.mariageplus.dto.weddingevent;

import com.mariageplus.entity.WeddingEventType;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Modification d'un événement : tous les champs sont optionnels.
 */
@Data
public class UpdateWeddingEventRequest {

    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    private WeddingEventType type;

    @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
    private String description;

    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;

    @Size(max = 200, message = "Le lieu ne doit pas dépasser 200 caractères")
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

    @Size(max = 1000, message = "L'URL map ne doit pas dépasser 1000 caractères")
    private String mapUrl;

    private Integer displayOrder;

    private Boolean active;
}
