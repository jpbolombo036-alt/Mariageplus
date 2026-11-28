package com.mariageplus.service;

import com.mariageplus.dto.venue.VenueDocumentSettingsDto;
import com.mariageplus.entity.VenueDocumentSettings;
import com.mariageplus.repository.VenueDocumentSettingsRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * En-tête des devis et reçus de la salle : le gérant saisit une fois sa raison
 * sociale, ses mentions légales (RCCM, Id. Nat., NIF), ses coordonnées, un pied
 * de page et son logo ; chaque PDF les reprend.
 */
@Service
@RequiredArgsConstructor
public class VenueDocumentSettingsService {

    /** Logo léger : il est relu à chaque PDF. */
    private static final int LOGO_MAX_BYTES = 1024 * 1024;

    private final VenueDocumentSettingsRepository repository;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public VenueDocumentSettingsDto get() {
        Long orgId = guard.requireEnabledOrganization("VENUE_VIEW");
        return toDto(repository.findById(orgId).orElse(null));
    }

    @Transactional
    public VenueDocumentSettingsDto update(VenueDocumentSettingsDto request) {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        VenueDocumentSettings s = repository.findById(orgId)
                .orElseGet(() -> VenueDocumentSettings.builder().organizationId(orgId).build());
        s.setLegalName(clean(request.getLegalName()));
        s.setRccm(clean(request.getRccm()));
        s.setIdNat(clean(request.getIdNat()));
        s.setNif(clean(request.getNif()));
        s.setAddress(clean(request.getAddress()));
        s.setPhone(clean(request.getPhone()));
        s.setEmail(clean(request.getEmail()));
        s.setFooterNote(clean(request.getFooterNote()));
        VenueDocumentSettings saved = repository.save(s);
        auditService.record("VENUE_DOCUMENT_SETTINGS", orgId, "VenueDocumentSettings",
                securityUtils.getCurrentUserId(), orgId, "Mise à jour de l'en-tête des devis et reçus");
        return toDto(saved);
    }

    /** Logo des documents : JPEG ou PNG (formats lus par le générateur PDF), 1 Mo max. */
    @Transactional
    public VenueDocumentSettingsDto setLogo(byte[] image) {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Fichier image vide ou manquant");
        }
        if (image.length > LOGO_MAX_BYTES) {
            throw new IllegalArgumentException("Logo trop volumineux (max 1 Mo)");
        }
        String contentType = ImageFormats.contentType(image);
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("Le logo doit être une image JPEG ou PNG");
        }
        VenueDocumentSettings s = repository.findById(orgId)
                .orElseGet(() -> VenueDocumentSettings.builder().organizationId(orgId).build());
        deleteStoredLogo(s);
        if (storageService.isEnabled()) {
            String key = "venue-documents/" + orgId + "/logo-" + System.currentTimeMillis() + ImageFormats.extension(contentType);
            storageService.upload(key, image, contentType);
            s.setLogoKey(key);
            s.setLogo(null);
        } else {
            s.setLogo(image);
        }
        VenueDocumentSettings saved = repository.save(s);
        auditService.record("VENUE_DOCUMENT_SETTINGS", orgId, "VenueDocumentSettings",
                securityUtils.getCurrentUserId(), orgId, "Nouveau logo des devis et reçus");
        return toDto(saved);
    }

    @Transactional
    public VenueDocumentSettingsDto deleteLogo() {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        Optional<VenueDocumentSettings> found = repository.findById(orgId);
        found.ifPresent(s -> {
            deleteStoredLogo(s);
            repository.save(s);
        });
        return toDto(found.orElse(null));
    }

    @Transactional(readOnly = true)
    public byte[] getLogo() {
        Long orgId = guard.requireEnabledOrganization("VENUE_VIEW");
        return logoBytes(orgId);
    }

    // ---- Utilisé par la génération des PDF (l'accès est déjà vérifié par l'appelant) ----

    public Optional<VenueDocumentSettings> settingsFor(Long organizationId) {
        return repository.findById(organizationId);
    }

    public byte[] logoBytes(Long organizationId) {
        VenueDocumentSettings s = repository.findById(organizationId).orElse(null);
        if (s == null) {
            return null;
        }
        if (s.getLogoKey() != null && !s.getLogoKey().isBlank()) {
            byte[] fromS3 = storageService.download(s.getLogoKey());
            if (fromS3 != null) {
                return fromS3;
            }
        }
        return s.getLogo() == null || s.getLogo().length == 0 ? null : s.getLogo();
    }

    private void deleteStoredLogo(VenueDocumentSettings s) {
        if (s.getLogoKey() != null && !s.getLogoKey().isBlank()) {
            storageService.delete(s.getLogoKey());
        }
        s.setLogoKey(null);
        s.setLogo(null);
    }

    private VenueDocumentSettingsDto toDto(VenueDocumentSettings s) {
        if (s == null) {
            return VenueDocumentSettingsDto.builder().hasLogo(false).build();
        }
        return VenueDocumentSettingsDto.builder()
                .legalName(s.getLegalName())
                .rccm(s.getRccm())
                .idNat(s.getIdNat())
                .nif(s.getNif())
                .address(s.getAddress())
                .phone(s.getPhone())
                .email(s.getEmail())
                .footerNote(s.getFooterNote())
                .hasLogo((s.getLogoKey() != null && !s.getLogoKey().isBlank()) || (s.getLogo() != null && s.getLogo().length > 0))
                .build();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
