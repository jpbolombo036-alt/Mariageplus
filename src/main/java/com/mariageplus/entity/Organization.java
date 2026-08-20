package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Organisation : périmètre métier propriétaire des mariages.
 * Une organisation peut regrouper plusieurs utilisateurs (membres) et plusieurs mariages.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
