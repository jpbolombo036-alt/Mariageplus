package com.mariageplus.dto.venue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Nouveau taux du jour fixé par la salle. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueExchangeRateRequest {

    /** Francs congolais pour 1 dollar américain. */
    @NotNull(message = "Le taux est requis")
    @DecimalMin(value = "1", message = "Le taux doit être d'au moins 1 CDF pour 1 USD")
    private BigDecimal cdfPerUsd;

    @Size(max = 255)
    private String note;
}
