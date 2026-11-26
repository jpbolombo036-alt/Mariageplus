package com.mariageplus.dto.venue;

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
}
