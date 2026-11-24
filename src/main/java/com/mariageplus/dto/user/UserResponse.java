package com.mariageplus.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private boolean active;
    private boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private List<String> roles;
    private List<String> permissions;
    private Long organizationId;
    /** Vrai après une réinitialisation SUPER_ADMIN : le front ouvre l'écran de changement. */
    private boolean mustChangePassword;
}