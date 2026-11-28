package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenueExpenseCategory;
import com.mariageplus.entity.VenuePaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueExpenseRequest {

    @NotNull(message = "Le poste de dépense est requis")
    private VenueExpenseCategory category;

    @NotBlank(message = "Le libellé est requis")
    @Size(max = 200)
    private String label;

    @NotNull(message = "Le montant est requis")
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    private BigDecimal amount;

    @NotNull(message = "La devise est requise")
    private VenueCurrency currency;

    /** Défaut : espèces. */
    private VenuePaymentMethod method;

    /** Salle concernée ; vide = dépense générale. */
    private Long venueId;

    @Size(max = 100)
    private String reference;

    /** Défaut : maintenant. */
    private LocalDateTime spentAt;

    @Size(max = 500)
    private String note;
}
