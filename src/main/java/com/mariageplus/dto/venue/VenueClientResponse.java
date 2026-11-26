package com.mariageplus.dto.venue;

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
public class VenueClientResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private String email;
    private String notes;
    private LocalDateTime createdAt;

    /** Rempli seulement sur la fiche détaillée. */
    private List<VenueBookingResponse> bookings;
}
