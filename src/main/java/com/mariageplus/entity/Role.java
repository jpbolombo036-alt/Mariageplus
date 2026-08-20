package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Rôle (ex : SUPER_ADMIN, ORGANISATEUR, GESTIONNAIRE_INVITES, AGENT_ACCUEIL).
 * Un rôle est identifié par un code stable et se voit attribuer des permissions
 * via {@link RolePermission}.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
