package com.mariageplus.dto.table;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Affectation d'un guest à une table. Seul le guestId est fourni par le client ;
 * table/mariage/périmètre/capacité sont résolus côté backend.
 */
@Data
public class AssignGuestRequest {

    @NotNull(message = "L'invité est requis")
    private Long guestId;
}