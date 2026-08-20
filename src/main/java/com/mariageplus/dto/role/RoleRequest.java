package com.mariageplus.dto.role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RoleRequest {

    @NotBlank(message = "Le code du rôle est requis")
    private String code;

    private String description;

    private Boolean active;

    private List<String> permissionCodes;
}