package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenuePaymentKind;
import com.mariageplus.entity.VenuePaymentMethod;
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
public class VenuePaymentResponse {

    private Long id;
    private Long bookingId;

    /** PAYMENT ou REFUND. */
    private VenuePaymentKind kind;
    private String receiptNumber;
    private BigDecimal amount;
    private VenueCurrency currency;
    private BigDecimal exchangeRate;
    private BigDecimal amountInBookingCurrency;
    private VenuePaymentMethod method;
    private String reference;
    private LocalDateTime paidAt;
    private String note;
}
