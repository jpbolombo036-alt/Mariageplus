package com.mariageplus.dto.venue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelVenueBookingRequest {

    @Size(max = 500)
    private String reason;

    /** Remboursement à enregistrer avec l'annulation (facultatif ; le reste est retenu). */
    @Valid
    private VenuePaymentRequest refund;
}
