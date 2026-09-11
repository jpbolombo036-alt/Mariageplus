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
 * Réglages plateforme. Lecture ouverte à tout utilisateur authentifié
 * (le front s'en sert pour masquer/désactiver les boutons), modification
 * réservée au SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Tag(name = "Réglages plateforme", description = "Interrupteurs globaux (écriture : SUPER_ADMIN)")
public class AdminSettingsController {

    private final AppSettingService appSettingService;

    @GetMapping("/whatsapp")
    @Operation(summary = "État de l'envoi WhatsApp (lecture : tout utilisateur authentifié)")
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
}