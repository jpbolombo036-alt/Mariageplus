package com.mariageplus.dto.bulksend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * État d'un envoi en masse. Les compteurs sont actualisés pendant le
 * traitement : le front peut interroger ce endpoint en boucle pour afficher
 * la progression ("148/200 envoyés, 3 échecs").
 *
 * <p>Attention : {@code sentCount} compte les messages ACCEPTÉS par l'API Meta,
 * pas les messages reçus. Seule la confirmation du webhook Meta
 * ({@code deliveredCount}) prouve que l'invité a bien reçu son invitation.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSendBatchResponse {

    private Long id;
    private Long weddingId;
    private String channel;
    private String status;
    private int totalCount;
    /**
     * Messages ACCEPTÉS par l'API Meta (journal = SENT). N'implique PAS que l'invité
     * ait reçu le message : une facture Meta impayée laisse la messagerie en pause,
     * les envois sont acceptés (wamid renvoyé) mais jamais livrés.
     */
    private int sentCount;
    /**
     * Messages dont la livraison est confirmée par le webhook Meta (delivered/read).
     * Reste à 0 si le webhook n'est pas abonné, ou si la messagerie Meta est en
     * pause : c'est le seul indicateur fiable de réception par l'invité.
     */
    private int deliveredCount;
    private int failedCount;
    private int skippedCount;
    private LocalDateTime createdAt;
}
