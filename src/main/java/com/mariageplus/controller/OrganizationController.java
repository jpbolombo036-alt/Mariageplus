package com.mariageplus.controller;

import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.dto.organization.OrganizationMemberResponse;
import com.mariageplus.dto.organization.OrganizationRequest;
import com.mariageplus.dto.organization.OrganizationResponse;
import com.mariageplus.exception.ForbiddenException;
import com.mariageplus.security.SecurityUtils;
import com.mariageplus.service.OrganizationMemberService;
import com.mariageplus.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Organisations. L'accès en lecture des organisations et de leurs membres est
 * limité au périmètre de l'utilisateur (isolation) : un utilisateur non
 * SUPER_ADMIN n'accède qu'à son organisation.
 */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Organisations", description = "Gestion des organisations et de leurs membres")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationMemberService organizationMemberService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Liste des organisations (SUPER_ADMIN)")
    public ResponseEntity<List<OrganizationResponse>> getAll() {
        return ResponseEntity.ok(organizationService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Organisation par ID (SUPER_ADMIN ou membre)")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable Long id) {
        assertOrganizationAccess(id);
        return ResponseEntity.ok(organizationService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Créer une organisation (SUPER_ADMIN)")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Modifier une organisation (SUPER_ADMIN)")
    public ResponseEntity<OrganizationResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.ok(organizationService.update(id, request));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activer / désactiver une organisation (SUPER_ADMIN)")
    public ResponseEntity<OrganizationResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.toggleActive(id));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Membres d'une organisation (SUPER_ADMIN ou organisateur du périmètre)")
    public ResponseEntity<List<OrganizationMemberResponse>> listMembers(@PathVariable Long id) {
        assertCanManageMembers(id);
        return ResponseEntity.ok(organizationMemberService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORGANISATEUR')")
    @Operation(summary = "Ajouter un membre à l'organisation (SUPER_ADMIN ou organisateur du périmètre)")
    public ResponseEntity<OrganizationMemberResponse> addMember(@PathVariable Long id,
                                                                @Valid @RequestBody OrganizationMemberRequest request) {
        assertCanManageMembers(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationMemberService.addMember(id, request));
    }

    private void assertOrganizationAccess(Long organizationId) {
        if (securityUtils.isSuperAdmin()) {
            return;
        }
        Long orgId = securityUtils.requireOrganizationId();
        if (!orgId.equals(organizationId)) {
            throw new ForbiddenException("Accès refusé : ressource hors de votre organisation");
        }
    }

    private void assertCanManageMembers(Long organizationId) {
        assertOrganizationAccess(organizationId);
        if (!securityUtils.isSuperAdmin()) {
            securityUtils.assertPermission("ORGANIZATION_MANAGE_MEMBERS");
        }
    }
}