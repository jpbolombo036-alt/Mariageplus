package com.mariageplus.dto.venue;

import com.mariageplus.dto.event.WeddingDetailsRequest;
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
public class CreateEventFromBookingRequest {

    /** Défaut : titre de la réservation ou « Événement de <client> ». */
    @Size(max = 150)
    private String name;

    /** Obligatoire si la réservation est de type WEDDING (noms des mariés). */
    @Valid
    private WeddingDetailsRequest weddingDetails;
}
