package com.mariageplus.dto.venue;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueBookingExtraResponse {

    private Long id;
    private String label;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
