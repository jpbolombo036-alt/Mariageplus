package com.mariageplus.controller;

import com.mariageplus.entity.OrganizationSetting;
import com.mariageplus.service.OrganizationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Réglages par organisation — SUPER_ADMIN uniquement.
 * PUT : true/false = override explicite, "inherit" (ou null) = hériter du global.
 */
@RestController
@RequestMapping("/api/admin/organizations/{organizationId}/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Réglages organisation (SUPER_ADMIN)", description = "Interrupteurs par organisation avec héritage du global")
public class AdminOrganizationSettingsController {

    private final OrganizationSettingsService organizationSettingsService;

    @GetMapping
    @Operation(summary = "Réglages d'une organisation (null = hérite du global) + valeurs effectives")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long organizationId) {
        return ResponseEntity.ok(build(organizationId));
    }

    @PutMapping
    @Operation(summary = "Modifier whatsappEnabled / eventCreationEnabled (true, false ou \"inherit\")")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long organizationId,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body != null) {
            if (body.containsKey("whatsappEnabled")) {
                organizationSettingsService.setWhatsappEnabled(organizationId,
                        parseOverride(body.get("whatsappEnabled")));
            }
            if (body.containsKey("eventCreationEnabled")) {
                organizationSettingsService.setEventCreationEnabled(organizationId,
                        parseOverride(body.get("eventCreationEnabled")));
            }
        }
        return ResponseEntity.ok(build(organizationId));
    }

    private Map<String, Object> build(Long organizationId) {
        OrganizationSetting s = organizationSettingsService.getOrEmpty(organizationId);
        Map<String, Object> result = new HashMap<>();
        result.put("whatsappEnabled", s == null ? null : s.getWhatsappEnabled());
        result.put("eventCreationEnabled", s == null ? null : s.getEventCreationEnabled());
        result.put("effectiveWhatsapp", organizationSettingsService.isWhatsappAllowed(organizationId));
        result.put("effectiveEventCreation", organizationSettingsService.isEventCreationAllowed(organizationId));
        return result;
    }

    private Boolean parseOverride(Object raw) {
        if (raw == null || "inherit".equalsIgnoreCase(String.valueOf(raw))) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
