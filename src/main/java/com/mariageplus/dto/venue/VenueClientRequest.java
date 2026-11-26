package com.mariageplus.dto.venue;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueClientRequest {

    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Le nom du client est requis")
    @Size(max = 100)
    private String lastName;

    @Size(max = 30)
    private String phone;

    @Email(message = "L'email doit être valide")
    @Size(max = 255)
    private String email;

    @Size(max = 2000)
    private String notes;
}
