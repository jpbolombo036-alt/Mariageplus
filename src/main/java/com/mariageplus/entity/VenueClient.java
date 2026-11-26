package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

/** Fiche client d'une salle : aucun compte n'est requis. */
@Entity
// Redéclaré ici : le @SQLRestriction de BaseEntity (@MappedSuperclass) n'est pas hérité.
@SQLRestriction("deleted_at IS NULL")
@Table(name = "venue_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueClient extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Format E.164 (ex : +243812345678). */
    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 2000)
    private String notes;

    public String getFullName() {
        return firstName == null || firstName.isBlank() ? lastName : firstName + " " + lastName;
    }
}
