package com.mariageplus.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utilitaires de sécurité : accès à l'utilisateur connecté, à son périmètre
 * organisation, et vérifications de permissions. Le SUPER_ADMIN est global
 * (bypass de l'isolation par organisation et de toutes les permissions).
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    public Long getCurrentUserId() {
        UserPrincipal principal = currentPrincipal();
        return principal != null ? principal.getId() : null;
    }

    public String getCurrentUsername() {
        UserPrincipal principal = currentPrincipal();
        return principal != null ? principal.getEmail() : null;
    }

    public Long getCurrentOrganizationId() {
        UserPrincipal principal = currentPrincipal();
        return principal != null ? principal.getOrganizationId() : null;
    }

    public List<String> getCurrentRoles() {
        UserPrincipal principal = currentPrincipal();
        return principal != null ? principal.getRoles() : List.of();
    }

    public boolean isSuperAdmin() {
        return getCurrentRoles().contains("SUPER_ADMIN");
    }

    public boolean hasRole(String role) {
        String target = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getCurrentRoles().stream().anyMatch(r -> ("ROLE_" + r).equalsIgnoreCase(target));
    }

    /**
     * Vérifie une permission granulaire (ex : "GUEST_CREATE"). SUPER_ADMIN bypass.
     */
    public boolean hasPermission(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (isSuperAdmin()) {
            return true;
        }
        UserPrincipal principal = currentPrincipal();
        return principal != null && principal.getPermissions().stream()
                .anyMatch(p -> p.equalsIgnoreCase(code));
    }

    /**
     * Lève une SecurityException (→ 403) si la permission est absente.
     */
    public void assertPermission(String code) {
        if (!hasPermission(code)) {
            throw new SecurityException("Accès refusé : permission " + code + " requise");
        }
    }

    public Long requireOrganizationId() {
        Long orgId = getCurrentOrganizationId();
        if (orgId == null) {
            throw new SecurityException("Aucune organisation définie pour l'utilisateur connecté");
        }
        return orgId;
    }

    /**
     * Vérifie que la ressource appartient bien à l'organisation de l'utilisateur.
     * Le SUPER_ADMIN (global) y échappe.
     */
    public void assertOrganizationAccess(Long resourceOrganizationId) {
        if (isSuperAdmin()) {
            return;
        }
        Long orgId = requireOrganizationId();
        if (resourceOrganizationId == null || !resourceOrganizationId.equals(orgId)) {
            throw new SecurityException("Accès refusé : ressource hors de votre organisation");
        }
    }

    /**
     * Vérifie que l'utilisateur connecté a le droit d'agir sur le mariage ciblé.
     * - SUPER_ADMIN / ORGANISATEUR : accès org-wide (l'organisation est déjà
     *   vérifiée par loadInOrgScope) → toujours OK.
     * - Agents (GESTIONNAIRE_INVITES / AGENT_ACCUEIL) : OK uniquement si
     *   {@code weddingId} figure dans leurs mariages assignés ({@code weddingIds}).
     * Lève une {@link SecurityException} (→ 403) sinon.
     */
    public void assertWeddingAccess(Long weddingId) {
        if (isSuperAdmin() || !isAgent()) {
            return;
        }
        UserPrincipal principal = currentPrincipal();
        if (weddingId == null || principal == null || principal.getWeddingIds() == null
                || !principal.getWeddingIds().contains(weddingId)) {
            throw new SecurityException("Accès refusé : mariage hors de votre périmètre");
        }
    }

    private boolean isAgent() {
        return getCurrentRoles().stream().anyMatch(r ->
                r.equals("GESTIONNAIRE_INVITES") || r.equals("AGENT_ACCUEIL"));
    }

    private UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        return null;
    }
}