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
    private int sentCount;
    private int failedCount;
    private int skippedCount;
    private LocalDateTime createdAt;
}
