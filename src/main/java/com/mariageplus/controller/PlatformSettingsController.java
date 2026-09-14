package com.mariageplus.controller;

import com.mariageplus.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Réglages plateforme lisibles par tout utilisateur AUTHENTIFIÉ : le front
 * s'en sert pour masquer/désactiver des boutons (ex. « Créer un événement »).
 * Aucune valeur sensible ici — la modification reste réservée au SUPER_ADMIN
 * via /api/admin/settings/**.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@Tag(name = "Réglages plateforme (lecture)", description = "Valeurs publiques authentifiées pour le masquage UI")
public class PlatformSettingsController {

    private final AppSettingService appSettingService;

    @GetMapping("/event-creation-enabled")
    @Operation(summary = "La création d'événements est-elle autorisée ? (tout utilisateur authentifié)")
    public ResponseEntity<Map<String, Object>> eventCreationEnabled() {
        return ResponseEntity.ok(Map.of(
                "enabled", appSettingService.isEventCreationEnabled()));
    }
}
