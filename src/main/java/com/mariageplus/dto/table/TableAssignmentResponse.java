package com.mariageplus.dto.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Variable pour une opération d'affectation / déplacement / retrait.
 * Ne contient que des données utiles, sans exposistion des entités JPA.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableAssignmentResponse {

    private Long assignmentId;
    private Long guestId;
    private String guestName;
    /** Accompagnants déclarés sur la fiche de l'invité (sont assis à la même table). */
    private int companions;
    private Long tableId;
    private String tableName;
    private LocalDateTime assignedAt;
}