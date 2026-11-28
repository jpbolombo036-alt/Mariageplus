package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * En-tête des devis et reçus d'une salle (une ligne par organisation) :
 * raison sociale, mentions légales (RCCM, Id. Nat., NIF), coordonnées,
 * pied de page et logo. Valeur vide = repli sur la fiche de l'organisation.
 */
@Entity
@Table(name = "venue_document_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueDocumentSettings {

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "legal_name", length = 150)
    private String legalName;

    @Column(length = 60)
    private String rccm;

    @Column(name = "id_nat", length = 60)
    private String idNat;

    @Column(length = 60)
    private String nif;

    @Column(length = 255)
    private String address;

    @Column(length = 60)
    private String phone;

    @Column(length = 150)
    private String email;

    /** Texte libre en bas des documents (ex : conditions d'annulation). */
    @Column(name = "footer_note", length = 500)
    private String footerNote;

    @Column(name = "logo_key", length = 500)
    private String logoKey;

    @Column
    private byte[] logo;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
