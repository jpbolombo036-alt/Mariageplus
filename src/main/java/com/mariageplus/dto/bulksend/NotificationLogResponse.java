package com.mariageplus.dto.bulksend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ligne du journal d'un envoi en masse : résultat pour une invitation
 * (SENT / FAILED / SKIPPED) avec la raison éventuelle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogResponse {

    private Long id;
    private Long invitationId;
    private Long guestId;
    private String channel;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
