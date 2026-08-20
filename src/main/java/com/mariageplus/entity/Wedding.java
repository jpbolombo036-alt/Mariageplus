package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Mariage : entité principale du périmètre organisationnel.
 * Appartient obligatoirement à une organisation ({@code organizationId}) :
 * l'isolation est vérifiée côté service via {@code OrganizationMember}.
 *
 * {@code createdAt}/{@code updatedAt}/{@code deletedAt} sont hérités de
 * {@link BaseEntity}. {@code createdBy}/{@code updatedBy} tracent l'utilisateur
 * à l'origine des modifications.
 */
@Entity
@Table(name = "weddings", indexes = {
        @Index(name = "idx_weddings_org", columnList = "organization_id"),
        @Index(name = "idx_weddings_org_status", columnList = "organization_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wedding extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "groom_first_name", length = 100)
    private String groomFirstName;

    @Column(name = "groom_last_name", length = 100)
    private String groomLastName;

    @Column(name = "bride_first_name", length = 100)
    private String brideFirstName;

    @Column(name = "bride_last_name", length = 100)
    private String brideLastName;

    @Column(name = "groom_photo_url", length = 1000)
    private String groomPhotoUrl;

    @Column(name = "bride_photo_url", length = 1000)
    private String bridePhotoUrl;

    @Column(name = "couple_photo_url", length = 1000)
    private String couplePhotoUrl;

    @Column(length = 2000)
    private String description;

    @Column(name = "welcome_message", length = 2000)
    private String welcomeMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private WeddingStatus status = WeddingStatus.DRAFT;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** Nom d'affichage calculé : "GroomFirstName GroomLastName & BrideFirstName BrideLastName". */
    public String getDisplayName() {
        return (groomFirstName == null ? "" : groomFirstName)
                + " " + (groomLastName == null ? "" : groomLastName)
                + " & " + (brideFirstName == null ? "" : brideFirstName)
                + " " + (brideLastName == null ? "" : brideLastName);
    }
}
