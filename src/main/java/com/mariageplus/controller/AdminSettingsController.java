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
        return ResponseEntity.ok(Map.of(
                "whatsappSendingEnabled", appSettingService.isWhatsappSendingEnabled()));
    }

    @PutMapping("/whatsapp")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activer / désactiver l'envoi WhatsApp (SUPER_ADMIN)")
    public ResponseEntity<Map<String, Object>> updateWhatsapp(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("enabled");
        if (raw == null) {
            throw new IllegalArgumentException("Le champ 'enabled' est requis (true/false)");
        }
        boolean enabled = Boolean.parseBoolean(String.valueOf(raw));
        appSettingService.setWhatsappSendingEnabled(enabled);
        return ResponseEntity.ok(Map.of("whatsappSendingEnabled", enabled));
    }
}