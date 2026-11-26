package com.mariageplus.dto.venue;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueClientAccessResponse {

    private Long userId;
    private String email;
    private Long eventId;

    /** true si un compte existait déjà avec cet email (son mot de passe actuel reste valable). */
    private boolean existingAccount;

    /** Mot de passe temporaire à transmettre au client (null si compte existant). Affiché une seule fois. */
    private String temporaryPassword;

    /** Numéro WhatsApp du client (E.164 sans +) pour lui transmettre ses accès. */
    private String whatsappNumber;
}
