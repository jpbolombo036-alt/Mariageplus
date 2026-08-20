package com.mariageplus.dto.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrganizationRequest {

    @NotBlank(message = "Le nom de l'organisation est requis")
    private String name;

    @Email(message = "L'email doit être valide")
    private String email;

    private String phone;
    private String address;

    private Boolean active;
}