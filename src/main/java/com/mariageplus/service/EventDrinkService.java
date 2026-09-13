package com.mariageplus.service;

import com.mariageplus.dto.drink.AvailableDrinkResponse;
import com.mariageplus.dto.drink.ToggleAvailableRequest;
import com.mariageplus.entity.Drink;
import com.mariageplus.entity.DrinkCatalogItem;
import com.mariageplus.entity.EventDrink;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.DrinkCatalogItemRepository;
import com.mariageplus.repository.DrinkRepository;
import com.mariageplus.repository.EventDrinkRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Déclaration de DISPONIBILITÉ des boissons du catalogue pour un événement.
 * L'ORGANISATEUR choisit les boissons que ses invités verront sur le RSVP.
 *
 * <p>Rétro-compatibilité : si l'événement n'a AUCUNE déclaration dans
 * {@code event_drinks}, on retombe sur les boissons actives de l'ancienne
 * table {@code drinks} (événements non migrés).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventDrinkService {

    private final EventDrinkRepository eventDrinkRepository;
    private final DrinkCatalogItemRepository catalogRepository;
    private final DrinkRepository drinkRepository;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** Base publique S3 (si définie : URL CDN directe pour les photos). */
    @Value("${storage.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    /* ============================ LECTURE ============================ */

    /** Catalogue complet avec le statut de disponibilité pour l'événement. */
    @Transactional(readOnly = true)
    public List<AvailableDrinkResponse> listForEvent(Long weddingId) {
        eventService.loadInOrgScope(weddingId);
        Map<Long, EventDrink> byCatalogId = eventDrinkRepository.findByWeddingId(weddingId).stream()
                .collect(Collectors.toMap(EventDrink::getCatalogItemId, ed -> ed, (a, b) -> a));
        return catalogRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(item -> toResponse(item, byCatalogId.get(item.getId())))
                .collect(Collectors.toList());
    }

    /* ============================ ÉCRITURE ============================ */

    /** Déclare (ou retire) la disponibilité d'une boisson du catalogue pour l'événement. */
    @Transactional
    public AvailableDrinkResponse toggle(Long weddingId, ToggleAvailableRequest request) {
        var event = eventService.loadInOrgScope(weddingId);
        DrinkCatalogItem item = catalogRepository.findById(request.getCatalogItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Boisson de catalogue non trouvée avec l'ID: " + request.getCatalogItemId()));
        if (!item.isActive()) {
            throw new IllegalArgumentException(
                    "La boisson '" + item.getName() + "' est désactivée du catalogue par le SUPER_ADMIN");
        }
        EventDrink eventDrink = eventDrinkRepository.findByWeddingIdAndCatalogItemId(weddingId, item.getId())
                .orElseGet(() -> EventDrink.builder().weddingId(weddingId).catalogItemId(item.getId()).build());
        eventDrink.setAvailable(Boolean.TRUE.equals(request.getAvailable()));
        EventDrink saved = eventDrinkRepository.save(eventDrink);
        auditService.record(saved.isAvailable() ? "EVENT_DRINK_ENABLE" : "EVENT_DRINK_DISABLE",
                saved.getId(), "EventDrink",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                (saved.isAvailable() ? "Disponibilité déclarée pour '" : "Disponibilité retirée pour '")
                        + item.getName() + "'");
        log.info("Événement #{} : boisson de catalogue '{}' → {}", weddingId, item.getName(), saved.isAvailable());
        return toResponse(item, saved);
    }

    /* ==================== INVITÉS (liste publique) ==================== */

    /**
     * Noms des boissons que les invités de cet événement peuvent choisir :
     * 1) les boissons du catalogue déclarées disponibles (pivot event_drinks) ;
     * 2) rétro-compat : si aucune déclaration, les boissons actives de
     *    l'ancienne table drinks (événements non migrés).
     */
    @Transactional(readOnly = true)
    public List<String> availableDrinkNames(Long weddingId) {
        List<EventDrink> declarations = eventDrinkRepository.findByWeddingIdAndAvailableTrue(weddingId);
        if (!declarations.isEmpty()) {
            List<Long> ids = declarations.stream().map(EventDrink::getCatalogItemId).toList();
            return catalogRepository.findAllById(ids).stream()
                    .filter(DrinkCatalogItem::isActive)
                    .map(DrinkCatalogItem::getName)
                    .toList();
        }
        // Rétro-compatibilité : aucun pivot → ancien comportement.
        return drinkRepository.findByWeddingIdAndActiveTrue(weddingId).stream()
                .map(Drink::getName)
                .toList();
    }

    /**
     * Boissons visibles par les INVITÉS (public) : items du catalogue déclarés
     * disponibles, avec leur photo. Fallback ancien comportement si aucun pivot.
     */
    @Transactional(readOnly = true)
    public List<AvailableDrinkResponse> listForInvitation(Long weddingId) {
        List<EventDrink> declarations = eventDrinkRepository.findByWeddingIdAndAvailableTrue(weddingId);
        if (declarations.isEmpty()) {
            // Rétro-compatibilité : boissons actives de l'ancienne table.
            return drinkRepository.findByWeddingIdAndActiveTrue(weddingId).stream()
                    .map(d -> AvailableDrinkResponse.builder()
                            .catalogItemId(d.getId())
                            .name(d.getName())
                            .description(d.getDescription())
                            .active(true)
                            .available(true)
                            .imageUrl(legacyImageUrl(d))
                            .build())
                    .collect(Collectors.toList());
        }
        List<Long> ids = declarations.stream().map(EventDrink::getCatalogItemId).toList();
        Map<Long, EventDrink> byId = declarations.stream()
                .collect(Collectors.toMap(EventDrink::getCatalogItemId, ed -> ed, (a, b) -> a));
        return catalogRepository.findAllById(ids).stream()
                .filter(DrinkCatalogItem::isActive)
                .map(item -> toResponse(item, byId.get(item.getId())))
                .sorted((a, b) -> {
                    Integer oa = a.getDisplayOrder(), ob = b.getDisplayOrder();
                    return Integer.compare(oa == null ? Integer.MAX_VALUE : oa, ob == null ? Integer.MAX_VALUE : ob);
                })
                .collect(Collectors.toList());
    }

    /* ============================ HELPERS ============================ */

    private AvailableDrinkResponse toResponse(DrinkCatalogItem item, EventDrink declaration) {
        return AvailableDrinkResponse.builder()
                .catalogItemId(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .displayOrder(declaration != null && declaration.getDisplayOrder() != null
                        ? declaration.getDisplayOrder() : item.getDisplayOrder())
                .active(item.isActive())
                .available(declaration != null && declaration.isAvailable())
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
        return "/api/admin/drink-catalog/" + item.getId() + "/image";
    }

    /** URL de la photo d'une boisson de l'ancienne table (endpoint public existant). */
    private String legacyImageUrl(Drink d) {
        boolean hasImage = (d.getImageKey() != null && !d.getImageKey().isBlank())
                || (d.getImage() != null && d.getImage().length > 0);
        if (!hasImage) {
            return null;
        }
        if (d.getImageKey() != null && !d.getImageKey().isBlank()
                && s3PublicBaseUrl != null && !s3PublicBaseUrl.isBlank()) {
            String base = s3PublicBaseUrl.trim();
            return base.endsWith("/") ? base + d.getImageKey() : base + "/" + d.getImageKey();
        }
        return "/api/events/" + d.getWeddingId() + "/drinks/" + d.getId() + "/image";
    }
}
