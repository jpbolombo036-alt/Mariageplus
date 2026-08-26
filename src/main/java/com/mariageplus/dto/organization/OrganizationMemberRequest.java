package com.mariageplus.dto.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Ajout d'un membre (utilisateur) à une organisation avec un rôle
 * (ex : GESTIONNAIRE_INVITES, AGENT_ACCUEIL, ORGANISATEUR).
 */
@Data
public class OrganizationMemberRequest {

    @NotBlank(message = "Le prénom est requis")
    private String firstName;

    @NotBlank(message = "Le nom est requis")
    private String lastName;

    @NotBlank(message = "L'email est requis")
    @Email(message = "L'email doit être valide")
    private String email;

    private String phone;

    @NotBlank(message = "Le mot de passe est requis")
    private String password;

    @NotBlank(message = "Le rôle est requis")
    private String roleCode;

    /**
     * Mariage assigné (scoping agent). Requis si {@code roleCode} ∈
     * {GESTIONNAIRE_INVITES, AGENT_ACCUEIL} ; ignoré pour SUPER_ADMIN / ORGANISATEUR.
     */
    private Long weddingId;
}