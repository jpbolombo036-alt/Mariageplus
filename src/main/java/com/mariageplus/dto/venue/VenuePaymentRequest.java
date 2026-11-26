package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenuePaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenuePaymentRequest {

    @NotNull(message = "Le montant est requis")
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    private BigDecimal amount;

    /** Défaut : devise de la réservation. */
    private VenueCurrency currency;

    /** CDF pour 1 USD ; obligatoire si la devise diffère de celle de la réservation. */
    @DecimalMin(value = "0.0001", message = "Le taux doit être positif")
    private BigDecimal exchangeRate;

    @NotNull(message = "Le mode de paiement est requis")
    private VenuePaymentMethod method;

    @Size(max = 100)
    private String reference;

    /** Défaut : maintenant. */
    private LocalDateTime paidAt;

    @Size(max = 500)
    private String note;
}
