package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Permission granulaire du système RBAC (ex : WEDDING_VIEW, GUEST_CREATE).
 * Une permission est associée à un ou plusieurs rôles via {@link RolePermission}.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends BaseEntity {

    @Column(unique = true, nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(length = 50)
    private String categorie;
}
