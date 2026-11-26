package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueRateRequest {

    @NotBlank(message = "Le libellé du tarif est requis")
    @Size(max = 100)
    private String label;

    @NotNull(message = "Le prix est requis")
    @DecimalMin(value = "0.00", message = "Le prix ne peut pas être négatif")
    private BigDecimal price;

    @NotNull(message = "La devise est requise (USD ou CDF)")
    private VenueCurrency currency;

    private Boolean active;
}
