package com.mariageplus.service;

import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.notification.ReminderSettingsDto;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.entity.EventType;
import com.mariageplus.entity.ReminderSettings;
import com.mariageplus.entity.UserNotification;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.ReminderSettingsRepository;
import com.mariageplus.repository.UserNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventReminderIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2031, 6, 10);
    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");
    private static final ReminderSettingsDto DEFAULTS = new ReminderSettingsDto(true, true, 7, true, true, 8, "Africa/Kinshasa");

    @Autowired private EventReminderService reminders;
    @Autowired private AuthService authService;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserNotificationRepository notifications;
    @Autowired private ReminderSettingsRepository settingsRepository;

    @AfterEach
    void clearSettings() {
        settingsRepository.deleteAll();
    }

    @Test
    void organizersAreRemindedOnceForTheFirstReminderTheEveAndTheDay() {
        LoginResponse alice = register("rappel-alice@example.com", "Organisation Rappel Alice");
        LoginResponse bruno = register("rappel-bruno@example.com", "Organisation Rappel Bruno");
        Long aliceOrg = alice.getUser().getOrganizationId();

        Event wedding = event(aliceOrg, "Mariage de Nadia", TODAY.plusDays(5), EventStatus.PUBLISHED);
        event(aliceOrg, "Mariage annulé", TODAY.plusDays(5), EventStatus.CANCELLED);
        event(aliceOrg, "Trop loin", TODAY.plusDays(8), EventStatus.PUBLISHED);
        event(aliceOrg, "Brouillon", TODAY, EventStatus.DRAFT);

        // Before the default sending hour (8h in Kinshasa): nothing.
        assertEquals(0, reminders.remindDue(at(TODAY, 7)));

        assertEquals(2, reminders.remindDue(at(TODAY, 9)));
        List<UserNotification> aliceInbox = inbox(alice);
        assertEquals(2, aliceInbox.size());
        UserNotification first = byEvent(aliceInbox, wedding.getId());
        assertEquals(UserNotification.TYPE_EVENT_REMINDER, first.getType());
        assertEquals("« Mariage de Nadia » a lieu dans 5 jours", first.getTitle());
        assertTrue(first.getMessage().startsWith("Dimanche 15 juin 2031 à 14h00 — Salle Lumière"), first.getMessage());
        assertTrue(first.getMessage().contains("Invités : 0 · Confirmés : 0 · Sans réponse : 0"), first.getMessage());
        assertTrue(aliceInbox.stream().anyMatch(n -> n.getMessage().contains("encore en brouillon")));
        assertTrue(inbox(bruno).isEmpty(), "another organization is never notified");

        // Hourly runs the same day send nothing new. Two days later the wedding gets
        // nothing more, but "Trop loin" has entered the 7-day window.
        assertEquals(0, reminders.remindDue(at(TODAY, 15)));
        assertEquals(1, reminders.remindDue(at(TODAY.plusDays(2), 9)));
        assertEquals("« Trop loin » a lieu dans 6 jours", inbox(alice).get(0).getTitle());

        assertEquals(1, reminders.remindDue(at(TODAY.plusDays(4), 9)));
        assertEquals("« Mariage de Nadia » a lieu demain", inbox(alice).get(0).getTitle());
        assertEquals(1, reminders.remindDue(at(TODAY.plusDays(5), 9)));
        assertEquals("« Mariage de Nadia » a lieu aujourd'hui", inbox(alice).get(0).getTitle());
        assertEquals(0, reminders.remindDue(at(TODAY.plusDays(5), 10)));

        // A new date restarts the reminders for that date.
        wedding.setEventDate(TODAY.plusDays(20));
        eventRepository.save(wedding);
        assertEquals(1, reminders.remindDue(at(TODAY.plusDays(14), 9)));
        assertEquals("« Mariage de Nadia » a lieu dans 6 jours", inbox(alice).get(0).getTitle());
    }

    @Test
    void theMostPreciseLevelWins() {
        LoginResponse carla = register("rappel-carla@example.com", "Organisation Rappel Carla");
        Long org = carla.getUser().getOrganizationId();
        LocalDate base = LocalDate.of(2032, 3, 1);
        Event quiet = event(org, "Fête calme", base.plusDays(3), EventStatus.PUBLISHED);
        Event loud = event(org, "Fête suivie", base.plusDays(3), EventStatus.PUBLISHED);

        // Organization switches reminders off; one event switches them back on with an evening hour.
        settings(ReminderSettings.Scope.ORGANIZATION, org, new ReminderSettingsDto(false, null, null, null, null, null, null));
        settings(ReminderSettings.Scope.EVENT, loud.getId(), new ReminderSettingsDto(true, null, null, null, null, 18, null));

        assertEquals(0, reminders.remindDue(at(base, 9)), "the evening hour of the event is not reached");
        assertEquals(1, reminders.remindDue(at(base, 18)));
        List<UserNotification> inbox = inbox(carla);
        assertEquals(1, inbox.size());
        assertEquals(loud.getId(), inbox.get(0).getEventId());
        assertTrue(inbox.stream().noneMatch(n -> quiet.getId().equals(n.getEventId())));

        // Global: first reminder 10 days before, eve off. Organization still off, event still on.
        settings(ReminderSettings.Scope.GLOBAL, 0L, new ReminderSettingsDto(null, null, 10, false, null, null, null));
        Event later = event(org, "Fête lointaine", base.plusDays(10), EventStatus.PUBLISHED);
        settings(ReminderSettings.Scope.EVENT, later.getId(), new ReminderSettingsDto(true, null, null, null, null, null, null));
        assertEquals(1, reminders.remindDue(at(base, 9)));
        assertEquals("« Fête lointaine » a lieu dans 10 jours", inbox(carla).get(0).getTitle());
        assertEquals(0, reminders.remindDue(at(base.plusDays(2), 18)), "eve reminder disabled globally");
    }

    @Test
    void reminderKinds() {
        assertEquals("J0", EventReminderService.kindFor(TODAY, TODAY, DEFAULTS));
        assertEquals("J1", EventReminderService.kindFor(TODAY, TODAY.plusDays(1), DEFAULTS));
        assertEquals("JX", EventReminderService.kindFor(TODAY, TODAY.plusDays(2), DEFAULTS));
        assertEquals("JX", EventReminderService.kindFor(TODAY, TODAY.plusDays(7), DEFAULTS));
        assertNull(EventReminderService.kindFor(TODAY, TODAY.plusDays(8), DEFAULTS));
        assertNull(EventReminderService.kindFor(TODAY, TODAY.minusDays(1), DEFAULTS));
        assertNull(EventReminderService.kindFor(TODAY, null, DEFAULTS));
        ReminderSettingsDto onlyDayOf = new ReminderSettingsDto(true, false, 7, false, true, 8, "UTC");
        assertNull(EventReminderService.kindFor(TODAY, TODAY.plusDays(1), onlyDayOf));
        assertNull(EventReminderService.kindFor(TODAY, TODAY.plusDays(5), onlyDayOf));
        assertEquals("J0", EventReminderService.kindFor(TODAY, TODAY, onlyDayOf));
    }

    private void settings(ReminderSettings.Scope scope, Long id, ReminderSettingsDto v) {
        ReminderSettings s = settingsRepository.findByScopeAndScopeId(scope, id)
                .orElseGet(() -> ReminderSettings.builder().scope(scope).scopeId(id).build());
        s.setEnabled(v.enabled());
        s.setFirstReminderEnabled(v.firstReminderEnabled());
        s.setFirstReminderDays(v.firstReminderDays());
        s.setEveEnabled(v.eveEnabled());
        s.setDayOfEnabled(v.dayOfEnabled());
        s.setSendHour(v.sendHour());
        s.setTimeZone(v.timeZone());
        settingsRepository.save(s);
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(KINSHASA).toInstant();
    }

    private List<UserNotification> inbox(LoginResponse who) {
        return notifications.findByUserId(who.getUser().getId(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    private static UserNotification byEvent(List<UserNotification> inbox, Long eventId) {
        return inbox.stream().filter(n -> eventId.equals(n.getEventId())).findFirst().orElseThrow();
    }

    private Event event(Long organizationId, String name, LocalDate date, EventStatus status) {
        return eventRepository.save(Event.builder()
                .organizationId(organizationId)
                .name(name)
                .type(EventType.WEDDING)
                .eventDate(date)
                .startTime(LocalTime.of(14, 0))
                .venueName("Salle Lumière")
                .status(status)
                .build());
    }

    private LoginResponse register(String email, String organizationName) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Organisateur");
        request.setLastName("Rappel");
        request.setEmail(email);
        request.setPassword("MotDePasse123");
        request.setOrganizationName(organizationName);
        return authService.register(request);
    }
}
