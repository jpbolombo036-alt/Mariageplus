package com.mariageplus.controller;

import com.mariageplus.security.SecurityUtils;
import com.mariageplus.service.OrganizationSettingsService;
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
 * La valeur renvoyée est EFFECTIVE pour l'utilisateur connecté : override de
 * son organisation s'il existe, sinon le réglage global. Aucune valeur
 * sensible ici — la modification reste réservée au SUPER_ADMIN via
 * /api/admin/settings/** et /api/admin/organizations/{id}/settings.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@Tag(name = "Réglages plateforme (lecture)", description = "Valeurs publiques authentifiées pour le masquage UI")
public class PlatformSettingsController {

    private final SecurityUtils securityUtils;
    private final OrganizationSettingsService organizationSettingsService;

    @GetMapping("/event-creation-enabled")
    @Operation(summary = "La création d'événements est-elle autorisée pour l'utilisateur connecté ?")
    public ResponseEntity<Map<String, Object>> eventCreationEnabled() {
        boolean enabled;
        if (securityUtils.isSuperAdmin()) {
            enabled = true;
        } else {
            enabled = organizationSettingsService.isEventCreationAllowed(
                    securityUtils.getCurrentOrganizationId());
        }
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }
}

