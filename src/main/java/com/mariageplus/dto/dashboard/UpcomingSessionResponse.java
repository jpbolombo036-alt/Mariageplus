package com.mariageplus.dto.dashboard;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Prochaine session (sous-étape) à venir d'un événement — alimente la carte
 * « Prochain événement » du dashboard. Valeurs calculées depuis les données
 * réelles (event_sessions + RSVP acceptés), rien en dur.
 */
@Value
@Builder
public class UpcomingSessionResponse {
    Long id;
    String name;
    String type;
    LocalDate sessionDate;
    LocalTime startTime;
    LocalTime endTime;
    String venueName;
    String city;
    Long expectedAttendees;
}
