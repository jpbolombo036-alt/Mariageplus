package com.mariageplus.service;

import com.mariageplus.exception.ForbiddenException;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Garde du module « Salles » : chaque opération du module vérifie que
 * l'organisation concernée a été activée par le SUPER_ADMIN
 * (organization_settings.venue_module_enabled), en plus des permissions.
 */
@Component
@RequiredArgsConstructor
public class VenueModuleGuard {

    static final String DISABLED_MESSAGE =
            "Le module Salles n'est pas activé pour votre organisation. Contactez l'administrateur de la plateforme.";

    private final OrganizationSettingsService organizationSettingsService;
    private final SecurityUtils securityUtils;

    /**
     * Vérifie la permission puis l'activation du module pour l'organisation de
     * l'utilisateur connecté, et retourne cette organisation.
     */
    public Long requireEnabledOrganization(String permission) {
        securityUtils.assertPermission(permission);
        Long organizationId = securityUtils.requireOrganizationId();
        assertEnabled(organizationId);
        return organizationId;
    }

    /** Vérifie l'appartenance d'une ressource à l'organisation et l'activation du module. */
    public void assertAccess(Long resourceOrganizationId) {
        securityUtils.assertOrganizationAccess(resourceOrganizationId);
        assertEnabled(resourceOrganizationId);
    }

    public boolean isEnabledForCurrentUser() {
        Long organizationId = securityUtils.getCurrentOrganizationId();
        return organizationId != null && organizationSettingsService.isVenueModuleEnabled(organizationId);
    }

    private void assertEnabled(Long organizationId) {
        if (!organizationSettingsService.isVenueModuleEnabled(organizationId)) {
            throw new ForbiddenException(DISABLED_MESSAGE);
        }
    }
}
