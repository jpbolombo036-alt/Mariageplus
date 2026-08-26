package com.mariageplus.service;

import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Génère un fichier iCalendar (.ics) pour un événement de mariage, afin que
 * l'invité puisse l'ajouter d'un clic à son calendrier (Google/Apple/Outlook).
 * Format RFC 5545 (heure UTC, saut de ligne CRLF, échappement des valeurs).
 */
@Service
public class IcsCalendarService {

    private static final DateTimeFormatter UTC_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /**
     * @return le contenu texte de l'ICS, ou {@code null} si l'événement n'a pas de date.
     */
    public String buildIcs(Wedding wedding, WeddingEvent event) {
        if (event == null || event.getEventDate() == null) {
            return null;
        }
        String weddingName = wedding != null ? wedding.getDisplayName() : "Notre mariage";
        String eventName = event.getName() == null ? weddingName : event.getName();

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//MariagePlus//Invitation//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:mariageplus-").append(event.getId() == null ? "event" : event.getId())
                .append("@invitation\r\n");
        sb.append("DTSTAMP:").append(formatUtc(Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime()))
                .append("\r\n");
        sb.append("DTSTART:").append(start(event)).append("\r\n");
        sb.append("DTEND:").append(end(event)).append("\r\n");
        sb.append("SUMMARY:").append(escape(eventName)).append("\r\n");
        String location = location(event);
        if (!location.isEmpty()) {
            sb.append("LOCATION:").append(escape(location)).append("\r\n");
        }
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append("DESCRIPTION:").append(escape(event.getDescription())).append("\r\n");
        }
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String start(WeddingEvent event) {
        if (event.getStartTime() == null) {
            return event.getEventDate().toString();
        }
        return formatUtc(event.getEventDate().atTime(event.getStartTime()));
    }

    private String end(WeddingEvent event) {
        LocalTime endTime = event.getEndTime();
        LocalDate day = event.getEventDate();
        if (endTime == null) {
            endTime = event.getStartTime();
        }
        if (endTime == null) {
            // Événement d'une journée entière : on termine le lendemain matin.
            return day.plusDays(1).toString();
        }
        return formatUtc(day.atTime(endTime));
    }

    private String formatUtc(java.time.LocalDateTime dt) {
        return dt.atOffset(ZoneOffset.UTC).format(UTC_DATE_TIME);
    }

    private String location(WeddingEvent event) {
        StringBuilder loc = new StringBuilder();
        if (event.getVenueName() != null) loc.append(event.getVenueName());
        if (event.getVenueAddress() != null) {
            if (loc.length() > 0) loc.append(", ");
            loc.append(event.getVenueAddress());
        }
        if (event.getCity() != null) {
            if (loc.length() > 0) loc.append(", ");
            loc.append(event.getCity());
        }
        return loc.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }
}