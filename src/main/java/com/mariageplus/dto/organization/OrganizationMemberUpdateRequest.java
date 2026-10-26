package com.mariageplus.dto.organization;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mutation d'un membre d'organisation : changement du mariage assigné
 * (scoping agent) et/ou changement de rôle. Les deux champs sont optionnels —
 * au moins l'un doit être fourni.
 *
 * Utilisé par {@code PUT /api/organizations/{orgId}/members/{memberId}}.
 */
@Data
public class OrganizationMemberUpdateRequest {

    /**
     * Nouveau mariage assigné (scoping). Requis si le rôle devient un rôle
     * d'agent (GESTIONNAIRE_INVITES / AGENT_ACCUEIL) et que le weddingId est
     * null. Ignoré pour SUPER_ADMIN / ORGANISATEUR.
     */
    private Long weddingId;

    /**
     * Nouveau code de rôle (ex: GESTIONNAIRE_INVITES, AGENT_ACCUEIL).
     * Si fourni, le rôle du membre est remplacé et le user role global est mis à jour.
     */
    private String roleCode;
}