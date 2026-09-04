package com.mariageplus.dto.bulksend;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Requête "envoyer en masse" pour un mariage.
 *
 * - {@code resend=false} (défaut) : cible les invitations jamais envoyées
 *   (statuts GENERATED / DRAFT).
 * - {@code resend=true} : relance les invitations déjà SENT (respecte le
 *   plafond de relances {@code app.invitation.max-reminders}).
 * - {@code onlyPendingRsvp=true} : restreint aux invitations envoyées sans
 *   réponse RSVP (implique la sémantique "resend").
 * - Filtres cumulables : {@code categoryId} (catégorie d'invités) et/ou
 *   {@code invitationIds} (liste explicite). Sans filtre → toutes les
 *   invitations du mariage correspondant au mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSendRequest {

    private Long categoryId;

    private List<Long> invitationIds;

    @Builder.Default
    private boolean resend = false;

    @Builder.Default
    private boolean onlyPendingRsvp = false;

    /** Canal d'envoi ; seul WHATSAPP est supporté en V1 (validé côté service). */
    @NotNull
    private String channel;
}
