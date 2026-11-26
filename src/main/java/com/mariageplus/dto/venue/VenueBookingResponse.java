package com.mariageplus.dto.venue;

import com.mariageplus.entity.EventType;
import com.mariageplus.entity.VenueBookingStatus;
import com.mariageplus.entity.VenueCurrency;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueBookingResponse {

    private Long id;
    private String reference;
    private Long venueId;
    private String venueName;
    private Long clientId;
    private String clientName;
    private String clientPhone;
    private String title;
    private EventType eventType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer guestCount;
    private VenueBookingStatus status;
    private VenueCurrency currency;
    private Long rateId;
    private BigDecimal basePrice;
    private BigDecimal extrasTotal;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balance;
    private LocalDateTime optionExpiresAt;
    private String notes;
    private String cancelReason;
    private Long eventId;
    private LocalDateTime createdAt;

    /** Rempli seulement sur le détail d'une réservation. */
    private List<VenueBookingExtraResponse> extras;

    /** Rempli seulement sur le détail d'une réservation. */
    private List<VenuePaymentResponse> payments;
}
