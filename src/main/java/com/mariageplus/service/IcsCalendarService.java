package com.mariageplus.service;

import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventSession;
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
     * Si {@code session} est fournie, ses date/lieu priment (sous-étape précise) ;
     * sinon on utilise ceux de l'événement racine.
     */
    public String buildIcs(Event event, EventSession session) {
        if (event == null) {
            return null;
        }
        LocalDate date = firstNonNull(session != null ? session.getSessionDate() : null, event.getEventDate());
        if (date == null) {
            return null;
        }
        LocalTime startT = firstNonNull(session != null ? session.getStartTime() : null, event.getStartTime());
        LocalTime endT = firstNonNull(session != null ? session.getEndTime() : null, event.getEndTime());
        String venueName = firstNonNull(session != null ? session.getVenueName() : null, event.getVenueName());
        String venueAddress = firstNonNull(session != null ? session.getVenueAddress() : null, event.getVenueAddress());
        String city = firstNonNull(session != null ? session.getCity() : null, event.getCity());
        String description = firstNonNull(session != null ? session.getDescription() : null, event.getDescription());

        String eventName = event.getName() == null ? "Notre événement" : event.getName();
        if (session != null && session.getName() != null && !session.getName().isBlank()) {
            eventName = session.getName();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//MariagePlus//Invitation//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        Long uid = session != null && session.getId() != null ? session.getId() : event.getId();
        sb.append("UID:mariageplus-").append(uid == null ? "event" : uid)
                .append("@invitation\r\n");
        sb.append("DTSTAMP:").append(formatUtc(Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime()))
                .append("\r\n");
        sb.append("DTSTART:").append(start(date, startT)).append("\r\n");
        sb.append("DTEND:").append(end(date, startT, endT)).append("\r\n");
        sb.append("SUMMARY:").append(escape(eventName)).append("\r\n");
        String location = location(venueName, venueAddress, city);
        if (!location.isEmpty()) {
            sb.append("LOCATION:").append(escape(location)).append("\r\n");
        }
        if (description != null && !description.isBlank()) {
            sb.append("DESCRIPTION:").append(escape(description)).append("\r\n");
        }
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String start(LocalDate date, LocalTime startT) {
        if (startT == null) {
            return date.toString();
        }
        return formatUtc(date.atTime(startT));
    }

    private String end(LocalDate date, LocalTime startT, LocalTime endT) {
        if (endT == null) {
            endT = startT;
        }
        if (endT == null) {
            // Événement d'une journée entière : on termine le lendemain matin.
            return date.plusDays(1).toString();
        }
        return formatUtc(date.atTime(endT));
    }

    private String formatUtc(java.time.LocalDateTime dt) {
        return dt.atOffset(ZoneOffset.UTC).format(UTC_DATE_TIME);
    }

    private String location(String venueName, String venueAddress, String city) {
        StringBuilder loc = new StringBuilder();
        if (venueName != null) loc.append(venueName);
        if (venueAddress != null) {
            if (loc.length() > 0) loc.append(", ");
            loc.append(venueAddress);
        }
        if (city != null) {
            if (loc.length() > 0) loc.append(", ");
            loc.append(city);
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