package com.mariageplus.dto.venue;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueClientAccessRequest {

    /** Défaut : email de la fiche client. */
    @Email(message = "L'email doit être valide")
    private String email;

    private String firstName;

    private String lastName;
}
