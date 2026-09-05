package com.mariageplus.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Session (sous-étape) exposée publiquement dans la réponse d'invitation —
 * alimente la section « Programme » de la page invité. Données minimales,
 * aucune information administrative. Champ purement additif de
 * {@link PublicInvitationResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicEventSessionResponse {

    private String name;

    /** Type de session (CIVIL_CEREMONY, RELIGIOUS_CEREMONY, RECEPTION, AFTER_PARTY, OTHER). */
    private String type;

    private String description;

    private LocalDate sessionDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String venueName;

    private String venueAddress;

    private String city;

    private String mapUrl;
}
