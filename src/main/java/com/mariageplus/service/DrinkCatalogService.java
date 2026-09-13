package com.mariageplus.service;

import com.mariageplus.dto.drink.CatalogDrinkResponse;
import com.mariageplus.dto.drink.CreateCatalogDrinkRequest;
import com.mariageplus.entity.DrinkCatalogItem;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ForbiddenException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.DrinkCatalogItemRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CATALOGUE GLOBAL des boissons — géré exclusivement par le SUPER_ADMIN.
 * Les organisateurs ne créent plus de boissons : ils déclarent disponibles
 * les éléments de ce catalogue pour leurs événements ({@link EventDrinkService}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrinkCatalogService {

    private final DrinkCatalogItemRepository catalogRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final StorageService storageService;

    /** Taille maximale de la photo d'une boisson (2 Mo). */
    private static final int IMAGE_MAX_BYTES = 2 * 1024 * 1024;

    /** Base publique S3 (si définie : URL CDN directe pour les photos). */
    @Value("${storage.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    /* ============================ LECTURE ============================ */

    @Transactional(readOnly = true)
    public List<CatalogDrinkResponse> list() {
        assertSuperAdmin();
        return catalogRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public byte[] getImage(Long catalogItemId) {
        // Public : la photo du catalogue est visible sur les cartes RSVP des invités.
        DrinkCatalogItem item = loadItem(catalogItemId);
        if (item.getImageKey() != null && !item.getImageKey().isBlank()) {
            byte[] fromS3 = storageService.download(item.getImageKey());
            if (fromS3 != null) {
                return fromS3;
            }
        }
        return (item.getImage() == null || item.getImage().length == 0) ? null : item.getImage();
    }

    /* ============================ ÉCRITURE ============================ */

    @Transactional
    public CatalogDrinkResponse create(CreateCatalogDrinkRequest request) {
        assertSuperAdmin();
        catalogRepository.findByNameIgnoreCase(request.getName().trim()).ifPresent(d -> {
            throw new ConflictException("Une boisson nommée '" + request.getName().trim() + "' existe déjà dans le catalogue");
        });
        DrinkCatalogItem item = DrinkCatalogItem.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();
        DrinkCatalogItem saved = catalogRepository.save(item);
        auditService.record("DRINK_CATALOG_CREATE", saved.getId(), "DrinkCatalogItem",
                securityUtils.getCurrentUserId(), null,
                "Création de la boisson de catalogue '" + saved.getName() + "'");
        log.info("Catalogue boissons : '{}' créé par le SUPER_ADMIN", saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public CatalogDrinkResponse update(Long catalogItemId, CreateCatalogDrinkRequest request) {
        assertSuperAdmin();
        DrinkCatalogItem item = loadItem(catalogItemId);
        item.setName(request.getName().trim());
        item.setDescription(request.getDescription());
        item.setDisplayOrder(request.getDisplayOrder());
        DrinkCatalogItem saved = catalogRepository.save(item);
        auditService.record("DRINK_CATALOG_UPDATE", saved.getId(), "DrinkCatalogItem",
                securityUtils.getCurrentUserId(), null,
                "Modification de la boisson de catalogue '" + saved.getName() + "'");
        return toResponse(saved);
    }

    /** Active / désactive une boisson du catalogue (elle reste visible mais ne peut plus être déclarée disponible). */
    @Transactional
    public CatalogDrinkResponse setActive(Long catalogItemId, boolean active) {
        assertSuperAdmin();
        DrinkCatalogItem item = loadItem(catalogItemId);
        item.setActive(active);
        DrinkCatalogItem saved = catalogRepository.save(item);
        auditService.record("DRINK_CATALOG_SET_ACTIVE", saved.getId(), "DrinkCatalogItem",
                securityUtils.getCurrentUserId(), null,
                (active ? "Activation" : "Désactivation") + " de la boisson de catalogue '" + saved.getName() + "'");
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long catalogItemId) {
        assertSuperAdmin();
        DrinkCatalogItem item = loadItem(catalogItemId);
        if (item.getImageKey() != null && !item.getImageKey().isBlank()) {
            storageService.delete(item.getImageKey());
        }
        item.softDelete();
        item.setImage(null);
        item.setImageKey(null);
        catalogRepository.save(item);
        auditService.record("DRINK_CATALOG_DELETE", catalogItemId, "DrinkCatalogItem",
                securityUtils.getCurrentUserId(), null,
                "Suppression de la boisson de catalogue '" + item.getName() + "'");
    }

    /* ============================ PHOTO ============================ */

    @Transactional
    public void uploadImage(Long catalogItemId, MultipartFile file) {
        assertSuperAdmin();
        DrinkCatalogItem item = loadItem(catalogItemId);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le fichier");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (bytes.length > IMAGE_MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 2 Mo)");
        }
        if (!isSupportedImage(bytes)) {
            throw new IllegalArgumentException("Format non supporté (JPEG, PNG, GIF, WebP)");
        }
        if (item.getImageKey() != null && !item.getImageKey().isBlank()) {
            storageService.delete(item.getImageKey());
            item.setImageKey(null);
        }
        item.setImage(bytes);
        DrinkCatalogItem saved = catalogRepository.save(item);
        log.info("Photo uploadée pour la boisson de catalogue '{}' ({} octets)", saved.getName(), bytes.length);
    }

    @Transactional
    public void deleteImage(Long catalogItemId) {
        assertSuperAdmin();
        DrinkCatalogItem item = loadItem(catalogItemId);
        if (item.getImageKey() != null && !item.getImageKey().isBlank()) {
            storageService.delete(item.getImageKey());
        }
        item.setImageKey(null);
        item.setImage(null);
        catalogRepository.save(item);
    }

    /* ============================ HELPERS ============================ */

    private void assertSuperAdmin() {
        if (!securityUtils.isSuperAdmin()) {
            throw new ForbiddenException("Seul le SUPER_ADMIN peut gérer le catalogue des boissons");
        }
    }

    private DrinkCatalogItem loadItem(Long id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Boisson de catalogue non trouvée avec l'ID: " + id));
    }

    private CatalogDrinkResponse toResponse(DrinkCatalogItem item) {
        return CatalogDrinkResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .displayOrder(item.getDisplayOrder())
                .active(item.isActive())
                .imageUrl(resolveImageUrl(item))
                .build();
    }

    private String resolveImageUrl(DrinkCatalogItem item) {
        boolean hasImage = (item.getImageKey() != null && !item.getImageKey().isBlank())
                || (item.getImage() != null && item.getImage().length > 0);
        if (!hasImage) {
            return null;
        }
        if (item.getImageKey() != null && !item.getImageKey().isBlank()
                && s3PublicBaseUrl != null && !s3PublicBaseUrl.isBlank()) {
            String base = s3PublicBaseUrl.trim();
            return base.endsWith("/") ? base + item.getImageKey() : base + "/" + item.getImageKey();
        }
        // Endpoint public de l'API (le front préfixe avec la base de l'API si relatif).
        return "/api/admin/drink-catalog/" + item.getId() + "/image";
    }

    private boolean isSupportedImage(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return true;
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return true;
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return true;
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
