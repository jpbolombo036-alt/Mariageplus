package com.mariageplus.dto.event;

import com.mariageplus.entity.EventDressCode;
import com.mariageplus.util.DressColors;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Modification partielle d'un événement (champs null = inchangés).
 * Si {@code weddingDetails} est fourni, l'événement doit être de type WEDDING.
 */
@Data
public class UpdateEventRequest {

    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

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

    @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90")
    @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180")
    @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180")
    private Double longitude;

    @Size(max = 1000, message = "L'URL de la carte ne doit pas dépasser 1000 caractères")
    @Pattern(regexp = "(?i)^https?://\\S+$", message = "L'URL de la carte doit commencer par http:// ou https://")
    private String mapUrl;

    /**
     * Couleurs de tenue demandées aux invités (facultatif). Maximum 3. Une liste
     * vide explicite remet à zéro le vestiaire.
     */
    @Size(max = DressColors.MAX, message = "Vous pouvez demander au maximum 3 couleurs de tenue")
    private java.util.List<EventDressCode> dressColors;

    private Integer displayOrder;

    private Boolean active;

    private WeddingDetailsRequest weddingDetails;
}
