package com.mariageplus.dto.guest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGuestRequest {

    @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
    private String firstName;

    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String lastName;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères")
    private String phone;

    @Email(message = "L'email doit être valide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    private String email;

    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
    private String address;

    private Long categoryId;

    @Min(value = 0, message = "Le nombre d'accompagnants ne peut pas être négatif")
    private Integer allowedCompanions;

    @Size(max = 1000, message = "Les notes ne doivent pas dépasser 1000 caractères")
    private String notes;

    private Boolean active;
}
