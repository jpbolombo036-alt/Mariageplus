package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenueExpenseCategory;
import com.mariageplus.entity.VenuePaymentMethod;
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
public class VenueExpenseResponse {
    private Long id;
    private VenueExpenseCategory category;
    private String label;
    private BigDecimal amount;
    private VenueCurrency currency;
    private VenuePaymentMethod method;
    private Long venueId;
    private String venueName;
    private String reference;
    private LocalDateTime spentAt;
    private String note;
    private Long recordedBy;
    private String recordedByName;
}
