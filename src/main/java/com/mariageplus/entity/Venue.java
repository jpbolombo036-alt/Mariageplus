package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

/** Salle de fête appartenant à une organisation (module « Salles »). */
@Entity
// Redéclaré ici : le @SQLRestriction de BaseEntity (@MappedSuperclass) n'est pas hérité.
@SQLRestriction("deleted_at IS NULL")
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venue extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String commune;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    private Double latitude;

    private Double longitude;

    @Column(name = "capacity_seated")
    private Integer capacitySeated;

    @Column(name = "capacity_standing")
    private Integer capacityStanding;

    /** Équipements, texte libre (ex : climatisation, groupe électrogène, parking). */
    @Column(length = 1000)
    private String amenities;

    /** Clé S3 de la photo principale (si le stockage objet est actif). */
    @Column(name = "image_key", length = 500)
    private String imageKey;

    /** Photo en fallback base de données. */
    @Column
    private byte[] image;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
