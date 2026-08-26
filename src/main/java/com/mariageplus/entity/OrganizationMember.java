package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Membre d'une organisation : lie un utilisateur à une organisation avec un rôle
 * dans ce périmètre (ex : GESTIONNAIRE_INVITES, AGENT_ACCUEIL).
 * C'est la clé de l'isolation multi-tenant : l'utilisateur n'accède qu'aux
 * données de l'organisation pour laquelle il est membre.
 */
@Entity
@Table(name = "organization_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Périmètre du membre. {@code null} = rôle org-wide (SUPER_ADMIN /
     * ORGANISATEUR / legacy). Non null pour les agents (GESTIONNAIRE_INVITES /
     * AGENT_ACCUEIL), qui sont scopés à un mariage précis.
     */
    @Column(name = "wedding_id")
    private Long weddingId;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
