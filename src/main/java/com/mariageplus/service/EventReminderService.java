package com.mariageplus.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mariageplus.dto.notification.ReminderSettingsDto;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.ReminderSettings;
import com.mariageplus.entity.EventReminderLog;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.UserNotification;
import com.mariageplus.repository.EventReminderLogRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.UserNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rappels automatiques des événements proches dans la cloche des organisateurs :
 * J7 (une fois, entre J-7 et J-2), J1 (la veille) et J0 (le jour J), avec les
 * chiffres utiles (réponses, invitations jamais ouvertes).
 *
 * <p>La tâche tourne toutes les heures mais n'envoie rien avant {@code send-from-hour}
 * dans le fuseau configuré : un redémarrage ou une heure manquée est rattrapé le
 * même jour. {@link EventReminderLog} garantit un seul envoi par rappel, y compris
 * avec plusieurs instances. Chaque événement est traité dans sa propre transaction :
 * l'échec de l'un ne bloque pas les autres.</p>
 */
@Service
@Slf4j
public class EventReminderService {

    static final String ORGANIZER_ROLE = "ORGANISATEUR";
    static final List<EventStatus> REMINDED_STATUSES = List.of(EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.ACTIVE);
    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", FR);
    private static final DateTimeFormatter TIME_FR = DateTimeFormatter.ofPattern("HH'h'mm", FR);

    private final EventRepository eventRepository;
    private final EventReminderLogRepository logRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserNotificationRepository notificationRepository;
    private final GuestRepository guestRepository;
    private final InvitationRepository invitationRepository;
    private final RsvpRepository rsvpRepository;
    private final TransactionTemplate transactionTemplate;
    private final ReminderSettingsService settingsService;

    /** Interrupteur d'exploitation (variable Railway) : coupe tout, quels que soient les réglages. */
    @Value("${app.event.reminders-enabled:true}")
    private boolean enabled;

    public EventReminderService(EventRepository eventRepository, EventReminderLogRepository logRepository,
                                OrganizationMemberRepository organizationMemberRepository,
                                UserNotificationRepository notificationRepository, GuestRepository guestRepository,
                                InvitationRepository invitationRepository, RsvpRepository rsvpRepository,
                                PlatformTransactionManager transactionManager, ReminderSettingsService settingsService) {
        this.eventRepository = eventRepository;
        this.logRepository = logRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.notificationRepository = notificationRepository;
        this.guestRepository = guestRepository;
        this.invitationRepository = invitationRepository;
        this.rsvpRepository = rsvpRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.settingsService = settingsService;
    }

    /** Toutes les heures : chaque événement est rappelé selon ses propres réglages (heure, fuseau). */
    @Scheduled(cron = "${app.event.reminder-cron:0 0 * * * *}")
    @SchedulerLock(name = "eventReminders", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        if (!enabled) {
            log.debug("Rappels d'événements désactivés par l'exploitation");
            return;
        }
        remindDue(Instant.now());
    }

    /** @return le nombre de rappels envoyés (un par événement et type). */
    public int remindDue(Instant now) {
        // Fenêtre large : le premier rappel peut aller jusqu'à 30 jours, et les fuseaux décalent la date d'un jour.
        LocalDate utcToday = now.atZone(ZoneOffset.UTC).toLocalDate();
        Map<Long, ReminderSettingsDto> organizations = new HashMap<>();
        int sent = 0;
        for (Event event : eventRepository.findByStatusInAndEventDateBetween(
                REMINDED_STATUSES, utcToday.minusDays(1), utcToday.plusDays(31))) {
            ReminderSettingsDto effective = ReminderSettingsService.effectiveForEvent(
                    settingsService.level(ReminderSettings.Scope.EVENT, event.getId()),
                    organizations.computeIfAbsent(event.getOrganizationId(), settingsService::organizationEffective));
            if (!Boolean.TRUE.equals(effective.enabled())) continue;
            ZonedDateTime local = now.atZone(zoneOf(effective));
            if (local.getHour() < effective.sendHour()) continue;
            LocalDate today = local.toLocalDate();
            String kind = kindFor(today, event.getEventDate(), effective);
            if (kind == null || logRepository.existsByEventIdAndKindAndEventDate(event.getId(), kind, event.getEventDate())) {
                continue;
            }
            try {
                Boolean done = transactionTemplate.execute(status -> remind(event, kind, today));
                if (Boolean.TRUE.equals(done)) sent++;
            } catch (DataIntegrityViolationException alreadySent) {
                log.debug("Rappel {} de l'événement {} déjà envoyé par une autre instance", kind, event.getId());
            } catch (RuntimeException ex) {
                log.warn("Rappel {} de l'événement {} non envoyé : {}", kind, event.getId(), ex.getMessage());
            }
        }
        if (sent > 0) log.info("Rappels d'événements : {} envoyé(s)", sent);
        return sent;
    }

    /**
     * J0 (jour J), J1 (veille) ou JX (premier rappel, une seule fois entre J-X et J-2),
     * selon les rappels activés pour l'événement.
     */
    static String kindFor(LocalDate today, LocalDate eventDate, ReminderSettingsDto s) {
        if (eventDate == null) return null;
        long days = ChronoUnit.DAYS.between(today, eventDate);
        if (days == 0) return Boolean.TRUE.equals(s.dayOfEnabled()) ? "J0" : null;
        if (days == 1) return Boolean.TRUE.equals(s.eveEnabled()) ? "J1" : null;
        boolean first = Boolean.TRUE.equals(s.firstReminderEnabled()) && s.firstReminderDays() != null;
        return first && days >= 2 && days <= s.firstReminderDays() ? "JX" : null;
    }

    private static ZoneId zoneOf(ReminderSettingsDto s) {
        try {
            return ZoneId.of(s.timeZone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Africa/Kinshasa");
        }
    }

    private boolean remind(Event event, String kind, LocalDate today) {
        // Journal d'abord : la contrainte unique arrête un doublon avant toute notification.
        logRepository.saveAndFlush(EventReminderLog.builder()
                .eventId(event.getId()).kind(kind).eventDate(event.getEventDate()).sentAt(LocalDateTime.now())
                .build());

        Set<Long> recipients = new LinkedHashSet<>();
        if (event.getOrganizationId() != null) {
            organizationMemberRepository.findActiveByOrganizationAndRole(event.getOrganizationId(), ORGANIZER_ROLE)
                    .stream().map(OrganizationMember::getUser).forEach(u -> recipients.add(u.getId()));
        }
        if (recipients.isEmpty()) return false;

        String title = title(event, kind, today);
        String message = message(event, kind);
        List<UserNotification> notifications = new ArrayList<>();
        for (Long userId : recipients) {
            notifications.add(UserNotification.builder()
                    .userId(userId)
                    .type(UserNotification.TYPE_EVENT_REMINDER)
                    .title(title)
                    .message(message)
                    .organizationId(event.getOrganizationId())
                    .eventId(event.getId())
                    .build());
        }
        notificationRepository.saveAll(notifications);
        return true;
    }

    static String title(Event event, String kind, LocalDate today) {
        String name = "« " + event.getName() + " »";
        return switch (kind) {
            case "J0" -> name + " a lieu aujourd'hui";
            case "J1" -> name + " a lieu demain";
            default -> name + " a lieu dans " + ChronoUnit.DAYS.between(today, event.getEventDate()) + " jours";
        };
    }

    private String message(Event event, String kind) {
        Long id = event.getId();
        long guests = guestRepository.countByWeddingId(id);
        long accepted = rsvpRepository.countByStatusForWedding(id, RsvpStatus.ACCEPTED);
        long noAnswer = invitationRepository.countNonRespondersByWeddingId(id);
        long neverOpened = invitationRepository.countByWeddingIdAndStatusAndOpenedAtIsNull(id, InvitationStatus.SENT);

        StringBuilder sb = new StringBuilder(when(event));
        sb.append("\nInvités : ").append(guests)
                .append(" · Confirmés : ").append(accepted)
                .append(" · Sans réponse : ").append(noAnswer)
                .append(" · Invitations jamais ouvertes : ").append(neverOpened);
        if (event.getStatus() == EventStatus.DRAFT) {
            sb.append("\nL'événement est encore en brouillon : publiez-le pour que vos invités puissent répondre.");
        }
        switch (kind) {
            case "JX" -> {
                if (noAnswer > 0) sb.append("\nPensez à relancer les invités qui n'ont pas répondu.");
            }
            case "J1" -> sb.append("\nVérifiez le plan de tables et les agents d'accueil.");
            default -> sb.append("\nBon événement ! Le check-in se fait depuis l'onglet Check-in.");
        }
        return sb.length() <= 2000 ? sb.toString() : sb.substring(0, 1999) + "…";
    }

    private static String when(Event event) {
        StringBuilder sb = new StringBuilder(capitalize(event.getEventDate().format(DATE_FR)));
        if (event.getStartTime() != null) sb.append(" à ").append(event.getStartTime().format(TIME_FR));
        if (event.getVenueName() != null && !event.getVenueName().isBlank()) sb.append(" — ").append(event.getVenueName().trim());
        return sb.toString();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
