package com.mariageplus.controller;

import com.mariageplus.dto.notification.ReminderSettingsDto;
import com.mariageplus.service.ReminderSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Réglages des rappels d'événements : global (SUPER_ADMIN), organisation et
 * événement (SUPER_ADMIN ou organisateurs de l'organisation). Le contrôle d'accès
 * est fait par {@link ReminderSettingsService}. Champ null = hériter.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Rappels d'événements", description = "Réglages à trois niveaux : global, organisation, événement")
public class ReminderSettingsController {

    private final ReminderSettingsService service;

    @GetMapping("/api/admin/reminder-settings")
    @Operation(summary = "Réglages globaux des rappels (SUPER_ADMIN)")
    public ResponseEntity<ReminderSettingsDto.View> getGlobal() {
        return ResponseEntity.ok(service.globalView());
    }

    @PutMapping("/api/admin/reminder-settings")
    @Operation(summary = "Modifier les réglages globaux des rappels (SUPER_ADMIN)")
    public ResponseEntity<ReminderSettingsDto.View> putGlobal(@RequestBody ReminderSettingsDto body) {
        return ResponseEntity.ok(service.updateGlobal(body));
    }

    @GetMapping("/api/organizations/{organizationId}/reminder-settings")
    @Operation(summary = "Réglages des rappels d'une organisation")
    public ResponseEntity<ReminderSettingsDto.View> getOrganization(@PathVariable Long organizationId) {
        return ResponseEntity.ok(service.organizationView(organizationId));
    }

    @PutMapping("/api/organizations/{organizationId}/reminder-settings")
    @Operation(summary = "Modifier les réglages des rappels d'une organisation")
    public ResponseEntity<ReminderSettingsDto.View> putOrganization(@PathVariable Long organizationId,
                                                                   @RequestBody ReminderSettingsDto body) {
        return ResponseEntity.ok(service.updateOrganization(organizationId, body));
    }

    @GetMapping("/api/events/{eventId}/reminder-settings")
    @Operation(summary = "Réglages des rappels d'un événement")
    public ResponseEntity<ReminderSettingsDto.View> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(service.eventView(eventId));
    }

    @PutMapping("/api/events/{eventId}/reminder-settings")
    @Operation(summary = "Modifier les réglages des rappels d'un événement")
    public ResponseEntity<ReminderSettingsDto.View> putEvent(@PathVariable Long eventId,
                                                            @RequestBody ReminderSettingsDto body) {
        return ResponseEntity.ok(service.updateEvent(eventId, body));
    }
}
