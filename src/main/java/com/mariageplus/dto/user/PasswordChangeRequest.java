package com.mariageplus.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordChangeRequest {

    @NotBlank(message = "L'ancien mot de passe est requis")
    private String oldPassword;

    @NotBlank(message = "Le nouveau mot de passe est requis")
    private String newPassword;
}