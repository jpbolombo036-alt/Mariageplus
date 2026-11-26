package com.mariageplus.dto.venue;

import com.mariageplus.entity.EventType;
import com.mariageplus.entity.VenueBookingStatus;
import com.mariageplus.entity.VenueCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Création (POST) ou modification partielle (PUT : champs null = inchangés) d'une réservation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueBookingRequest {

    /** Requis à la création. */
    private Long venueId;

    /** Client existant… */
    private Long clientId;

    /** …ou nouveau client créé en même temps que la réservation. */
    @Valid
    private VenueClientRequest client;

    @Size(max = 150)
    private String title;

    private EventType eventType;

    /** Requis à la création. */
    private LocalDateTime startAt;

    /** Requis à la création. */
    private LocalDateTime endAt;

    @Min(value = 0)
    private Integer guestCount;

    /** Défaut : devise du tarif choisi, sinon USD. */
    private VenueCurrency currency;

    private Long rateId;

    /** Défaut : prix du tarif choisi. */
    @DecimalMin(value = "0.00", message = "Le prix ne peut pas être négatif")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.00", message = "La remise ne peut pas être négative")
    private BigDecimal discount;

    @Size(max = 2000)
    private String notes;

    /** Création seulement : DEMANDE, OPTION (défaut) ou CONFIRMEE. */
    private VenueBookingStatus status;

    /** Création d'une OPTION : nombre de jours avant expiration sans acompte (défaut : réglage serveur). */
    @Min(value = 1)
    private Integer optionDays;
}
