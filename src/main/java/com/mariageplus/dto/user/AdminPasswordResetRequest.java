package com.mariageplus.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminPasswordResetRequest {

    @NotBlank(message = "Le nouveau mot de passe est requis")
    @Size(min = 8, max = 128, message = "Le nouveau mot de passe doit contenir entre 8 et 128 caractères")
    private String newPassword;
}
