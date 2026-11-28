package com.mariageplus.dto.venue;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** En-tête des devis et reçus : lecture (avec hasLogo) et modification (champs texte). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueDocumentSettingsDto {

    @Size(max = 150)
    private String legalName;

    @Size(max = 60)
    private String rccm;

    @Size(max = 60)
    private String idNat;

    @Size(max = 60)
    private String nif;

    @Size(max = 255)
    private String address;

    @Size(max = 60)
    private String phone;

    @Email(message = "L'email doit être valide")
    @Size(max = 150)
    private String email;

    @Size(max = 500)
    private String footerNote;

    /** Lecture seule : un logo est enregistré. */
    private boolean hasLogo;
}
