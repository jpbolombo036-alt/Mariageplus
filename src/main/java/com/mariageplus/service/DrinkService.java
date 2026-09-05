package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.drink.CreateDrinkRequest;
import com.mariageplus.dto.drink.DrinkResponse;
import com.mariageplus.dto.drink.UpdateDrinkRequest;
import com.mariageplus.entity.Drink;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.DrinkMapper;
import com.mariageplus.repository.DrinkRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrinkService {

    private final DrinkRepository drinkRepository;
    private final DrinkMapper drinkMapper;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final StorageService storageService;

    /** Taille maximale de la photo d'une boisson (2 Mo). */
    private static final int IMAGE_MAX_BYTES = 2 * 1024 * 1024;

    /** Base publique S3 (si définie : URL CDN directe pour les photos). */
    @Value("${storage.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    /** URL publique de CETTE API (sert /api/events/{wid}/drinks/{id}/image). */
    @Value("${app.whatsapp.public-api-base-url:}")
    private String publicApiBaseUrl;

    @Transactional
    public DrinkResponse create(Long weddingId, CreateDrinkRequest request) {
        securityUtils.assertPermission("DRINK_CREATE");
        Event event = eventService.loadInOrgScope(weddingId);

        Drink drink = Drink.builder()
                .weddingId(weddingId)
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();
        Drink saved = drinkRepository.save(drink);
        auditService.record("DRINK_CREATE", saved.getId(), "Drink",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création de la boisson '" + saved.getName() + "'");
        return toResponse(saved);
    }

    public PageResponse<DrinkResponse> list(Long weddingId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("DRINK_VIEW");
        eventService.loadInOrgScope(weddingId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Drink> drinkPage = drinkRepository.findByWeddingId(weddingId, pageable);
        List<DrinkResponse> content = drinkPage.getContent().stream()
                .map(this::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, drinkPage);
    }

    public DrinkResponse getById(Long weddingId, Long drinkId) {
        securityUtils.assertPermission("DRINK_VIEW");
        eventService.loadInOrgScope(weddingId);
        return toResponse(loadDrink(weddingId, drinkId));
    }

    @Transactional
    public DrinkResponse update(Long weddingId, Long drinkId, UpdateDrinkRequest request) {
        securityUtils.assertPermission("DRINK_UPDATE");
        Event event = eventService.loadInOrgScope(weddingId);
        Drink drink = loadDrink(weddingId, drinkId);
        drinkMapper.updateFromRequest(request, drink);
        Drink saved = drinkRepository.save(drink);
        auditService.record("DRINK_UPDATE", saved.getId(), "Drink",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Modification de la boisson");
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long weddingId, Long drinkId) {
        securityUtils.assertPermission("DRINK_DELETE");
        eventService.loadInOrgScope(weddingId);
        Drink drink = loadDrink(weddingId, drinkId);
        drink.softDelete();
        drinkRepository.save(drink);
    }

    public List<DrinkResponse> listActive(Long weddingId) {
        return drinkRepository.findByWeddingIdAndActiveTrue(weddingId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Upload / remplace la photo d'une boisson (mécanique identique aux photos
     * d'événement : S3 si configuré, base de données en fallback).
     */
    @Transactional
    public void setImage(Long weddingId, Long drinkId, byte[] image) {
        securityUtils.assertPermission("DRINK_UPDATE");
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Fichier image vide ou manquant");
        }
        if (image.length > IMAGE_MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 2 Mo)");
        }
        if (!isSupportedImage(image)) {
            throw new IllegalArgumentException("Format d'image non supporté (JPEG, PNG, GIF ou WebP attendu)");
        }
        Event event = eventService.loadInOrgScope(weddingId);
        Drink drink = loadDrink(weddingId, drinkId);
        if (storageService.isEnabled()) {
            if (drink.getImageKey() != null && !drink.getImageKey().isBlank()) {
                storageService.delete(drink.getImageKey());
            }
            String key = "drinks/" + weddingId + "/" + drinkId + "-" + System.currentTimeMillis() + extensionOf(image);
            storageService.upload(key, image, contentTypeOf(image));
            drink.setImageKey(key);
            drink.setImage(null);
        } else {
            drink.setImage(image);
        }
        drinkRepository.save(drink);
        auditService.record("DRINK_UPDATE", drinkId, "Drink",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Mise à jour de la photo de la boisson '" + drink.getName() + "'");
    }

    /** Photo d'une boisson (S3 d'abord, base en fallback) ; null si aucune. */
    @Transactional(readOnly = true)
    public byte[] getImage(Long weddingId, Long drinkId) {
        Drink drink = loadDrink(weddingId, drinkId);
        if (drink.getImageKey() != null && !drink.getImageKey().isBlank()) {
            byte[] fromS3 = storageService.download(drink.getImageKey());
            if (fromS3 != null) {
                return fromS3;
            }
        }
        return (drink.getImage() == null || drink.getImage().length == 0) ? null : drink.getImage();
    }

    /** Supprime la photo d'une boisson. */
    @Transactional
    public void deleteImage(Long weddingId, Long drinkId) {
        securityUtils.assertPermission("DRINK_UPDATE");
        Drink drink = loadDrink(weddingId, drinkId);
        if (drink.getImageKey() != null && !drink.getImageKey().isBlank()) {
            storageService.delete(drink.getImageKey());
        }
        drink.setImageKey(null);
        drink.setImage(null);
        drinkRepository.save(drink);
    }

    /** Mapping + URL publique de la photo (CDN S3 sinon endpoint API public). */
    private DrinkResponse toResponse(Drink drink) {
        DrinkResponse response = drinkMapper.toResponse(drink);
        response.setImageUrl(resolveImageUrl(drink));
        return response;
    }

    private String resolveImageUrl(Drink drink) {
        if (drink.getImageKey() != null && !drink.getImageKey().isBlank()
                && s3PublicBaseUrl != null && !s3PublicBaseUrl.isBlank()) {
            String base = s3PublicBaseUrl.trim();
            return base.endsWith("/") ? base + drink.getImageKey() : base + "/" + drink.getImageKey();
        }
        if (publicApiBaseUrl != null && !publicApiBaseUrl.isBlank()) {
            String base = publicApiBaseUrl.trim();
            String path = "/api/events/" + drink.getWeddingId() + "/drinks/" + drink.getId() + "/image";
            return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
        }
        return null;
    }

    private boolean isSupportedImage(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return true;
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return true;
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return true;
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private String extensionOf(byte[] image) {
        if ((image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8) return ".jpg";
        if ((image[0] & 0xFF) == 0x89 && image[1] == 'P') return ".png";
        if (image[0] == 'G' && image[1] == 'I') return ".gif";
        return ".webp";
    }

    private String contentTypeOf(byte[] image) {
        if ((image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8) return "image/jpeg";
        if ((image[0] & 0xFF) == 0x89 && image[1] == 'P') return "image/png";
        if (image[0] == 'G' && image[1] == 'I') return "image/gif";
        return "image/webp";
    }

    private Drink loadDrink(Long weddingId, Long drinkId) {
        return drinkRepository.findByIdAndWeddingId(drinkId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Boisson non trouvée avec l'ID: " + drinkId + " pour le mariage " + weddingId));
    }
}
