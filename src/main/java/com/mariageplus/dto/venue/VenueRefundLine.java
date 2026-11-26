package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenuePaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un remboursement de la période, pour la liste du tableau de bord. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueRefundLine {

    private Long paymentId;
    private String receiptNumber;
    private Long bookingId;
    private String bookingReference;
    private String clientName;
    private String venueName;
    private BigDecimal amount;
    private VenueCurrency currency;
    private VenuePaymentMethod method;
    private String reference;
    private LocalDateTime paidAt;
}
