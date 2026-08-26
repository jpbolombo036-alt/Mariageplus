package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.UpdateWeddingRequest;
import com.mariageplus.dto.wedding.UpdateWeddingStatusRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingStatus;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.WeddingMapper;
import com.mariageplus.repository.WeddingRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Module mariages. Centralise la logique métier et l'isolation par organisation.
 *
 * Règle de sécurité : chaque opération vérifie d'abord la permission granulaire
 * ({@link SecurityUtils#assertPermission}), puis le périmètre organisationnel
 * ({@link SecurityUtils#assertOrganizationAccess}) — jamais la permission seule.
 * Le SUPER_ADMIN possède un accès global (bypass de l'isolation).
 */
@Service
@RequiredArgsConstructor
public class WeddingService {

    private final WeddingRepository weddingRepository;
    private final WeddingMapper weddingMapper;
    private final SecurityUtils securityUtils;
    private final OrganizationService organizationService;
    private final AuditService auditService;

    @Transactional
    public WeddingResponse create(CreateWeddingRequest request) {
        securityUtils.assertPermission("WEDDING_CREATE");
        Long organizationId = resolveOrganizationForCreate(request.getOrganizationId());
        Long userId = securityUtils.getCurrentUserId();

        Wedding wedding = Wedding.builder()
                .organizationId(organizationId)
                .groomFirstName(request.getGroomFirstName())
                .groomLastName(request.getGroomLastName())
                .brideFirstName(request.getBrideFirstName())
                .brideLastName(request.getBrideLastName())
                .groomPhotoUrl(request.getGroomPhotoUrl())
                .bridePhotoUrl(request.getBridePhotoUrl())
                .couplePhotoUrl(request.getCouplePhotoUrl())
                .description(request.getDescription())
                .welcomeMessage(request.getWelcomeMessage())
                .status(WeddingStatus.DRAFT)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        Wedding saved = weddingRepository.save(wedding);
        auditService.record("WEDDING_CREATE", saved.getId(), "Wedding", userId, organizationId,
                "Création du mariage '" + saved.getDisplayName() + "'");
        return weddingMapper.toResponse(saved);
    }

    public WeddingResponse getById(Long id) {
        securityUtils.assertPermission("WEDDING_VIEW");
        Wedding wedding = loadForScope(id);
        return weddingMapper.toResponse(wedding);
    }

    public PageResponse<WeddingResponse> list(int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("WEDDING_VIEW");
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Wedding> weddingPage;
        if (securityUtils.isSuperAdmin()) {
            weddingPage = weddingRepository.findAll(pageable);
        } else {
            Long organizationId = securityUtils.requireOrganizationId();
            weddingPage = weddingRepository.findByOrganizationId(organizationId, pageable);
        }

        List<WeddingResponse> content = weddingPage.getContent().stream()
                .map(weddingMapper::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, weddingPage);
    }
    @Transactional
    public WeddingResponse update(Long id, UpdateWeddingRequest request) {
        securityUtils.assertPermission("WEDDING_UPDATE");
        Wedding wedding = loadForScope(id);
        applyUpdate(wedding, request);
        wedding.setUpdatedBy(securityUtils.getCurrentUserId());
        Wedding saved = weddingRepository.save(wedding);
        auditService.record("WEDDING_UPDATE", saved.getId(), "Wedding",
                securityUtils.getCurrentUserId(), saved.getOrganizationId(), "Modification du mariage");
        return weddingMapper.toResponse(saved);
    }

    @Transactional
    public WeddingResponse updateStatus(Long id, UpdateWeddingStatusRequest request) {
        WeddingStatus target = parseStatus(request.getStatus());
        securityUtils.assertPermission(permissionForStatus(target));

        Wedding wedding = loadForScope(id);
        if (!wedding.getStatus().canTransitionTo(target)) {
            throw new IllegalArgumentException("Transition de statut invalide : "
                    + wedding.getStatus() + " → " + target);
        }
        WeddingStatus previous = wedding.getStatus();
        wedding.setStatus(target);
        wedding.setUpdatedBy(securityUtils.getCurrentUserId());
        Wedding saved = weddingRepository.save(wedding);
        auditService.record("WEDDING_STATUS", saved.getId(), "Wedding",
                securityUtils.getCurrentUserId(), saved.getOrganizationId(),
                "Changement de statut : " + previous + " → " + target);
        return weddingMapper.toResponse(saved);
    }

    /**
     * Suppression logique (soft-delete) : le mariage est marqué deleted_at et
     * exclu des requêtes (filtre {@code deleted_at IS NULL} de BaseEntity).
     * Stratégie retenue car le mariage contiendra plus tard des données liées
     * (events, guests, invitations, RSVP, check-ins, tables) : on ne supprime
     * pas physiquement pour préserver l'intégrité. La permission WEDDING_DELETE
     * est respectée.
     */
    @Transactional
    public void delete(Long id) {
        securityUtils.assertPermission("WEDDING_DELETE");
        Wedding wedding = loadForScope(id);
        wedding.setUpdatedBy(securityUtils.getCurrentUserId());
        wedding.softDelete();
        weddingRepository.save(wedding);
        auditService.record("WEDDING_DELETE", id, "Wedding",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(), "Suppression logique du mariage");
    }

    private void applyUpdate(Wedding wedding, UpdateWeddingRequest request) {
        if (request.getGroomFirstName() != null) wedding.setGroomFirstName(request.getGroomFirstName());
        if (request.getGroomLastName() != null) wedding.setGroomLastName(request.getGroomLastName());
        if (request.getBrideFirstName() != null) wedding.setBrideFirstName(request.getBrideFirstName());
        if (request.getBrideLastName() != null) wedding.setBrideLastName(request.getBrideLastName());
        if (request.getGroomPhotoUrl() != null) wedding.setGroomPhotoUrl(request.getGroomPhotoUrl());
        if (request.getBridePhotoUrl() != null) wedding.setBridePhotoUrl(request.getBridePhotoUrl());
        if (request.getCouplePhotoUrl() != null) wedding.setCouplePhotoUrl(request.getCouplePhotoUrl());
        if (request.getDescription() != null) wedding.setDescription(request.getDescription());
        if (request.getWelcomeMessage() != null) wedding.setWelcomeMessage(request.getWelcomeMessage());
    }

    /**
     * Charge le mariage ET vérifie qu'il appartient au périmètre autorisé.
     */
    private Wedding loadForScope(Long id) {
        return loadInOrgScope(id);
    }

    /**
     * Méthode publique de chargement + vérification du périmètre organisationnel.
     * Réutilisée par les modules enfants (ex : événements) afin de centraliser
     * le contrôle d'accès à un mariage.
     */
    public Wedding loadInOrgScope(Long id) {
        Wedding wedding = weddingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mariage non trouvé avec l'ID: " + id));
        // Scoping wedding (agents scopés à un ou plusieurs mariages précis).
        securityUtils.assertWeddingAccess(id);
        securityUtils.assertOrganizationAccess(wedding.getOrganizationId());
        return wedding;
    }

    /**
     * Détermine le périmètre de création : pour un ORGANISATEUR, l'organisation
     * est déduite de l'utilisateur connecté (jamais fournie arbitrairement) ;
     * pour un SUPER_ADMIN, l'organisation cible doit être précisée explicitement.
     */
    private Long resolveOrganizationForCreate(Long requestedOrganizationId) {
        if (securityUtils.isSuperAdmin()) {
            if (requestedOrganizationId == null) {
                throw new IllegalArgumentException("organizationId requis pour créer un mariage (SUPER_ADMIN)");
            }
            organizationService.getOrganization(requestedOrganizationId); // vérifie l'existence
            return requestedOrganizationId;
        }
        return securityUtils.requireOrganizationId();
    }

    private WeddingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Statut requis");
        }
        try {
            return WeddingStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Statut invalide : " + raw);
        }
    }

    private String permissionForStatus(WeddingStatus target) {
        return switch (target) {
            case PUBLISHED -> "WEDDING_PUBLISH";
            case ARCHIVED -> "WEDDING_ARCHIVE";
            default -> "WEDDING_UPDATE";
        };
    }
}