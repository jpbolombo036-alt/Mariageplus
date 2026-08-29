package com.mariageplus.dto.event;

import com.mariageplus.entity.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Création d'un événement (nouvelle racine unifiée).
 * Le {@code organizationId} est ignoré pour un ORGANISATEUR (périmètre déduit
 * de l'utilisateur connecté) ; il est utilisé uniquement par un SUPER_ADMIN.
 *
 * {@code weddingDetails} est requis si {@code type = WEDDING} et rejeté
 * (400) pour tout autre type (décision D2 du document de conception).
 */
@Data
public class CreateEventRequest {

    @NotBlank(message = "Le nom de l'événement est requis")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    @NotNull(message = "Le type d'événement est requis")
    private EventType type;

    @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
    private String description;

    @Size(max = 2000, message = "Le message d'invitation ne doit pas dépasser 2000 caractères")
    private String message;

    private LocalDate eventDate;

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

    /** Requis uniquement si type = WEDDING. */
    private WeddingDetailsRequest weddingDetails;

    private Long organizationId;
}
