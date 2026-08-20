package com.mariageplus.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Informations légères de l'utilisateur connecté, injectées via @CurrentUser.
 */
@Data
@AllArgsConstructor
public class UserInfo {
    private Long id;
    private String email;
    private Long organizationId;
}