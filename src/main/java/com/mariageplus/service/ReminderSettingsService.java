package com.mariageplus.service;

import com.mariageplus.dto.notification.ReminderSettingsDto;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.ReminderSettings;
import com.mariageplus.entity.ReminderSettings.Scope;
import com.mariageplus.exception.ForbiddenException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.OrganizationRepository;
import com.mariageplus.repository.ReminderSettingsRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Réglages des rappels à trois niveaux. Le plus précis gagne :
 * événement → organisation → global (SUPER_ADMIN) → valeurs du serveur.
 *
 * <p>Qui règle : le SUPER_ADMIN partout ; un organisateur (rôle ORGANISATEUR actif
 * dans l'organisation) pour son organisation et ses événements.</p>
 */
@Service
@RequiredArgsConstructor
public class ReminderSettingsService {

    static final long GLOBAL_ID = 0L;
    static final String ORGANIZER_ROLE = "ORGANISATEUR";

    private final ReminderSettingsRepository repository;
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final SecurityUtils securityUtils;

    @Value("${app.event.reminder-zone:Africa/Kinshasa}")
    private String defaultZone;

    @Value("${app.event.reminder-send-from-hour:8}")
    private int defaultSendHour;

    /** Valeurs du serveur, sous le niveau global. */
    public ReminderSettingsDto serverDefaults() {
        return new ReminderSettingsDto(true, true, 7, true, true, defaultSendHour, defaultZone);
    }

    // ----- Résolution (tâche planifiée) -----

    @Transactional(readOnly = true)
    public ReminderSettingsDto effectiveGlobal() {
        return level(Scope.GLOBAL, GLOBAL_ID).over(serverDefaults());
    }

    @Transactional(readOnly = true)
    public ReminderSettingsDto level(Scope scope, Long id) {
        return repository.findByScopeAndScopeId(scope, id).map(ReminderSettingsDto::from).orElse(ReminderSettingsDto.empty());
    }

    /** Valeurs effectives d'un événement. Le fuseau ne se règle jamais au niveau événement. */
    public static ReminderSettingsDto effectiveForEvent(ReminderSettingsDto eventLevel, ReminderSettingsDto orgEffective) {
        ReminderSettingsDto withoutZone = new ReminderSettingsDto(eventLevel.enabled(), eventLevel.firstReminderEnabled(),
                eventLevel.firstReminderDays(), eventLevel.eveEnabled(), eventLevel.dayOfEnabled(), eventLevel.sendHour(), null);
        return withoutZone.over(orgEffective);
    }

    // ----- Écrans de réglages -----

    @Transactional(readOnly = true)
    public ReminderSettingsDto.View globalView() {
        assertSuperAdmin();
        return new ReminderSettingsDto.View(Scope.GLOBAL.name(), GLOBAL_ID, level(Scope.GLOBAL, GLOBAL_ID), serverDefaults(), true);
    }

    @Transactional(readOnly = true)
    public ReminderSettingsDto.View organizationView(Long organizationId) {
        assertCanManageOrganization(organizationId);
        return new ReminderSettingsDto.View(Scope.ORGANIZATION.name(), organizationId,
                level(Scope.ORGANIZATION, organizationId), effectiveGlobal(), true);
    }

    @Transactional(readOnly = true)
    public ReminderSettingsDto.View eventView(Long eventId) {
        Event event = loadManageableEvent(eventId);
        return new ReminderSettingsDto.View(Scope.EVENT.name(), eventId,
                level(Scope.EVENT, eventId), organizationEffective(event.getOrganizationId()), false);
    }

    @Transactional
    public ReminderSettingsDto.View updateGlobal(ReminderSettingsDto values) {
        assertSuperAdmin();
        save(Scope.GLOBAL, GLOBAL_ID, values);
        return globalView();
    }

    @Transactional
    public ReminderSettingsDto.View updateOrganization(Long organizationId, ReminderSettingsDto values) {
        assertCanManageOrganization(organizationId);
        save(Scope.ORGANIZATION, organizationId, values);
        return organizationView(organizationId);
    }

    @Transactional
    public ReminderSettingsDto.View updateEvent(Long eventId, ReminderSettingsDto values) {
        loadManageableEvent(eventId);
        if (values.timeZone() != null) {
            throw new IllegalArgumentException("Le fuseau horaire se règle au niveau de l'organisation");
        }
        save(Scope.EVENT, eventId, values);
        return eventView(eventId);
    }

    @Transactional(readOnly = true)
    public ReminderSettingsDto organizationEffective(Long organizationId) {
        ReminderSettingsDto global = effectiveGlobal();
        return organizationId == null ? global : level(Scope.ORGANIZATION, organizationId).over(global);
    }

    private void save(Scope scope, Long scopeId, ReminderSettingsDto v) {
        validate(v);
        ReminderSettings s = repository.findByScopeAndScopeId(scope, scopeId)
                .orElseGet(() -> ReminderSettings.builder().scope(scope).scopeId(scopeId).build());
        s.setEnabled(v.enabled());
        s.setFirstReminderEnabled(v.firstReminderEnabled());
        s.setFirstReminderDays(v.firstReminderDays());
        s.setEveEnabled(v.eveEnabled());
        s.setDayOfEnabled(v.dayOfEnabled());
        s.setSendHour(v.sendHour());
        s.setTimeZone(v.timeZone() == null || v.timeZone().isBlank() ? null : v.timeZone().trim());
        s.setUpdatedAt(LocalDateTime.now());
        s.setUpdatedBy(securityUtils.getCurrentUserId());
        repository.save(s);
    }

    static void validate(ReminderSettingsDto v) {
        if (v.firstReminderDays() != null && (v.firstReminderDays() < 2 || v.firstReminderDays() > 30)) {
            throw new IllegalArgumentException("Le premier rappel doit avoir lieu entre 2 et 30 jours avant l'événement");
        }
        if (v.sendHour() != null && (v.sendHour() < 0 || v.sendHour() > 23)) {
            throw new IllegalArgumentException("L'heure d'envoi doit être comprise entre 0 et 23");
        }
        if (v.timeZone() != null && !v.timeZone().isBlank()) {
            try {
                ZoneId.of(v.timeZone().trim());
            } catch (DateTimeException ex) {
                throw new IllegalArgumentException("Fuseau horaire inconnu : " + v.timeZone());
            }
        }
    }

    // ----- Accès -----

    private void assertSuperAdmin() {
        if (!securityUtils.isSuperAdmin()) {
            throw new ForbiddenException("Réservé à l'administrateur de la plateforme");
        }
    }

    private void assertCanManageOrganization(Long organizationId) {
        if (organizationId == null || !organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organisation introuvable");
        }
        if (securityUtils.isSuperAdmin()) return;
        Long userId = securityUtils.getCurrentUserId();
        if (userId == null || !organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_CodeAndActiveTrue(
                userId, organizationId, ORGANIZER_ROLE)) {
            throw new ForbiddenException("Seuls les organisateurs de cette organisation peuvent régler ses rappels");
        }
    }

    private Event loadManageableEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable"));
        if (event.getOrganizationId() == null) {
            assertSuperAdmin();
        } else {
            assertCanManageOrganization(event.getOrganizationId());
        }
        return event;
    }
}
