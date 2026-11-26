package com.mariageplus.dto.venue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class VenueBookingExtraRequest {

    @NotBlank(message = "Le libellé de la prestation est requis")
    @Size(max = 150)
    private String label;

    @Min(value = 1, message = "La quantité doit être au moins 1")
    private Integer quantity;

    @NotNull(message = "Le prix unitaire est requis")
    @DecimalMin(value = "0.00", message = "Le prix ne peut pas être négatif")
    private BigDecimal unitPrice;
}
