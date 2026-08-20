package com.mariageplus.dto.invitation;

import lombok.Data;

/**
 * Modification administrative d'une invitation (ex : changement de statut, horodatage d'envoi).
 */
@Data
public class UpdateInvitationRequest {

    private String status;

    private java.time.LocalDateTime sentAt;

    private java.time.LocalDateTime lastSentAt;
}
