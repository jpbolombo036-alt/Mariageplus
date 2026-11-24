package com.mariageplus.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactMessageRequest {

    @NotBlank(message = "Le message est requis")
    @Size(max = 2000, message = "Le message ne doit pas dépasser 2000 caractères")
    private String message;
}
