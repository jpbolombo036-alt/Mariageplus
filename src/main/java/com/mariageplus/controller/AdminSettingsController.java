package com.mariageplus.controller;

import com.mariageplus.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Réglages plateforme. Lecture ET écriture réservées au SUPER_ADMIN
 * (l'organisateur ne doit même pas voir les interrupteurs globaux).
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Tag(name = "Réglages plateforme", description = "Interrupteurs globaux (écriture : SUPER_ADMIN)")
public class AdminSettingsController {

    private final AppSettingService appSettingService;

    @GetMapping("/whatsapp")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "État de l'envoi WhatsApp (lecture : SUPER_ADMIN uniquement)")
    public ResponseEntity<Map<String, Object>> getWhatsapp() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("whatsappSendingEnabled", appSettingService.isWhatsappSendingEnabled());
        body.put("whatsappMaxReminders", appSettingService.getWhatsappMaxReminders());
        return ResponseEntity.ok(body);
    }

    @PutMapping("/whatsapp")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activer / désactiver l'envoi WhatsApp et régler le plafond de relances (SUPER_ADMIN)")
    public ResponseEntity<Map<String, Object>> updateWhatsapp(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new java.util.HashMap<>();
        boolean hasEnabled = body != null && body.containsKey("enabled");
        boolean hasMax = body != null && body.containsKey("whatsappMaxReminders");
        if (!hasEnabled && !hasMax) {
            throw new IllegalArgumentException(
                    "Au moins un champ est requis : 'enabled' (true/false) ou 'whatsappMaxReminders' (entier >= 0 ou null)");
        }
        if (hasEnabled) {
            Object raw = body.get("enabled");
            boolean enabled = Boolean.parseBoolean(String.valueOf(raw));
            appSettingService.setWhatsappSendingEnabled(enabled);
            response.put("whatsappSendingEnabled", enabled);
        }
        if (hasMax) {
            Object raw = body.get("whatsappMaxReminders");
            Integer maxReminders;
            if (raw == null || "inherit".equalsIgnoreCase(String.valueOf(raw))) {
                maxReminders = appSettingService.setWhatsappMaxReminders(null);
            } else {
                try {
                    maxReminders = appSettingService.setWhatsappMaxReminders(
                            Integer.parseInt(String.valueOf(raw).trim()));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "'whatsappMaxReminders' doit être un entier >= 0 (ou null pour hériter)");
                }
            }
            response.put("whatsappMaxReminders", maxReminders);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/event-creation")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "État de l'interrupteur « création d'événements » (SUPER_ADMIN)")
    public ResponseEntity<Map<String, Object>> getEventCreation() {
        return ResponseEntity.ok(Map.of(
                "eventCreationEnabled", appSettingService.isEventCreationEnabled()));
    }

    @PutMapping("/event-creation")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Autoriser / interdire aux utilisateurs de créer des événements (SUPER_ADMIN)")
    public ResponseEntity<Map<String, Object>> updateEventCreation(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("enabled");
        if (raw == null) {
            throw new IllegalArgumentException("Champ 'enabled' requis (true/false)");
        }
        boolean enabled = Boolean.parseBoolean(String.valueOf(raw));
        appSettingService.setEventCreationEnabled(enabled);
        return ResponseEntity.ok(Map.of("eventCreationEnabled", enabled));
    }
}