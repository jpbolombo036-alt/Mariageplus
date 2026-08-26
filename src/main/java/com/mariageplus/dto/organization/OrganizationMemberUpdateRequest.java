package com.mariageplus.dto.organization;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mutation du périmètre d'un membre : changement du mariage assigné.
 * Utilisé par {@code PUT /api/organizations/{orgId}/members/{memberId}}.
 */
@Data
public class OrganizationMemberUpdateRequest {

    @NotNull(message = "Le weddingId est requis")
    private Long weddingId;
}