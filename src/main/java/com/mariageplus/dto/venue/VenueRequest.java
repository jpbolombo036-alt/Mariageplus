package com.mariageplus.dto.venue;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Création / modification d'une salle (PUT : champs null = inchangés, sauf le nom qui reste requis). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueRequest {

    @NotBlank(message = "Le nom de la salle est requis")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String commune;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String country;

    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double latitude;

    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double longitude;

    @Min(value = 0, message = "La capacité ne peut pas être négative")
    private Integer capacitySeated;

    @Min(value = 0, message = "La capacité ne peut pas être négative")
    private Integer capacityStanding;

    @Size(max = 1000)
    private String amenities;

    /** null = inchangé (true à la création). */
    private Boolean active;
}
