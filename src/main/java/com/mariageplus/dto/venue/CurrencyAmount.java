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
public class CurrencyAmount {

    private VenueCurrency currency;
    private BigDecimal amount;
}
