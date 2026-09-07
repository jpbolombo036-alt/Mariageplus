package com.mariageplus.dto.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représentation publique d'une table avec son occupation. assignedCount et
 * remainingCapacity sont calculés (jamais dénormalisés en base).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeddingTableResponse {

    private Long id;
    private String name;
    private String description;
    private int capacity;
    /** Nombre d'invités affectés (1 ligne par invité). */
    private long assignedGuests;
    /** Places réellement occupées : 1 + accompagnants déclarés par invité affecté. */
    private long assignedCount;
    private long remainingCapacity;
}