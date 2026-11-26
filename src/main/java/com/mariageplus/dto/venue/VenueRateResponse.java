package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueRateResponse {

    private Long id;
    private Long venueId;
    private String label;
    private BigDecimal price;
    private VenueCurrency currency;
    private boolean active;
}
