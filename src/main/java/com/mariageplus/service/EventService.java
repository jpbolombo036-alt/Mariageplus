package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.event.CreateEventRequest;
import com.mariageplus.dto.event.EventResponse;
import com.mariageplus.dto.event.UpdateEventRequest;
import com.mariageplus.dto.event.UpdateEventStatusRequest;
import com.mariageplus.dto.event.WeddingDetailsRequest;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventSession;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.entity.EventType;
import com.mariageplus.entity.WeddingDetails;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.EventMapper;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.EventSessionRepository;
import com.mariageplus.repository.WeddingDetailsRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module événements (nouvelle racine métier unifiée — Phase 1, coexistence
 * avec {@link WeddingService}).
 *
 * Règles :
 * - Le type WEDDING exige {@code weddingDetails} ; tout autre type le rejette (400).
 * - L'isolation par organisation est portée par {@code Event.organizationId}.
 * - Permissions réutilisées du socle existant (EVENT_*, WEDDING_PUBLISH...).
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final WeddingDetailsRepository weddingDetailsRepository;
    private final EventSessionRepository eventSessionRepository;
    private final EventMapper eventMapper;
    private final SecurityUtils securityUtils;
    private final StorageService storageService;
    private final AuditService auditService;

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        securityUtils.assertPermission("EVENT_CREATE");
        validateWeddingDetailsPresence(request.getType(), request.getWeddingDetails());
        Long organizationId = resolveOrganizationForCreate(request.getOrganizationId());
        Long userId = securityUtils.getCurrentUserId();

        Event event = Event.builder()
                .organizationId(organizationId)
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .message(request.getMessage())
                .eventDate(request.getEventDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .venueName(request.getVenueName())
                .venueAddress(request.getVenueAddress())
                .city(request.getCity())
                .commune(request.getCommune())
                .country(request.getCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .mapUrl(request.getMapUrl())
                .status(EventStatus.DRAFT)
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        validateTimes(event.getStartTime(), event.getEndTime());

        Event saved = eventRepository.save(event);

        if (request.getType() == EventType.WEDDING) {
            WeddingDetails details = WeddingDetails.builder()
                    .eventId(saved.getId())
                    .groomFirstName(request.getWeddingDetails().getGroomFirstName())
                    .groomLastName(request.getWeddingDetails().getGroomLastName())
                    .brideFirstName(request.getWeddingDetails().getBrideFirstName())
                    .brideLastName(request.getWeddingDetails().getBrideLastName())
                    .groomPhotoUrl(request.getWeddingDetails().getGroomPhotoUrl())
                    .bridePhotoUrl(request.getWeddingDetails().getBridePhotoUrl())
                    .couplePhotoUrl(request.getWeddingDetails().getCouplePhotoUrl())
                    .welcomeMessage(request.getWeddingDetails().getWelcomeMessage())
                    .build();
            weddingDetailsRepository.save(details);
        }

        auditService.record("EVENT_CREATE", saved.getId(), "Event", userId, organizationId,
                "Création de l'événement '" + saved.getName() + "' (" + saved.getType() + ")");
        return loadFullResponse(saved);
    }

    public EventResponse getById(Long id) {
        securityUtils.assertPermission("EVENT_VIEW");
        Event event = loadInOrgScope(id);
        return loadFullResponse(event);
    }

    public PageResponse<EventResponse> list(int page, int size, String sortBy, String sortDir, EventType type) {
        securityUtils.assertPermission("EVENT_VIEW");
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Event> eventPage;
        if (securityUtils.isSuperAdmin()) {
            eventPage = (type == null)
                    ? eventRepository.findAll(pageable)
                    : eventRepository.findByType(type, pageable);
        } else if (securityUtils.isAgentRole()) {
            // Scoping agent : GESTIONNAIRE_INVITES / AGENT_ACCUEIL ne voient QUE les
            // événements qui leur sont assignés (weddingIds), pas toute l'organisation.
            List<Long> scopedIds = securityUtils.getCurrentWeddingIds();
            if (scopedIds.isEmpty()) {
                eventPage = new PageImpl<>(List.of(), pageable, 0);
            } else {
                Long organizationId = securityUtils.requireOrganizationId();
                eventPage = (type == null)
                        ? eventRepository.findByOrganizationIdAndIdIn(organizationId, scopedIds, pageable)
                        : eventRepository.findByOrganizationIdAndTypeAndIdIn(organizationId, type, scopedIds, pageable);
            }
        } else {
            Long organizationId = securityUtils.requireOrganizationId();
            eventPage = (type == null)
                    ? eventRepository.findByOrganizationId(organizationId, pageable)
                    : eventRepository.findByOrganizationIdAndType(organizationId, type, pageable);
        }

        List<EventResponse> content = eventPage.getContent().stream()
                .map(this::loadFullResponse).collect(Collectors.toList());
        return PageResponse.of(content, eventPage);
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest request) {
        securityUtils.assertPermission("EVENT_UPDATE");
        Event event = loadInOrgScope(id);
        if (request.getWeddingDetails() != null && event.getType() != EventType.WEDDING) {
            throw new IllegalArgumentException(
                    "weddingDetails n'est autorisé que pour un événement de type WEDDING");
        }
        applyUpdate(event, request);
        validateTimes(event.getStartTime(), event.getEndTime());
        event.setUpdatedBy(securityUtils.getCurrentUserId());

        Event saved = eventRepository.save(event);

        if (request.getWeddingDetails() != null) {
            upsertWeddingDetails(saved.getId(), request.getWeddingDetails());
        }

        auditService.record("EVENT_UPDATE", saved.getId(), "Event",
                securityUtils.getCurrentUserId(), saved.getOrganizationId(), "Modification de l'événement");
        return loadFullResponse(saved);
    }

    private final OrganizationService organizationService;

    @Transactional
    public EventResponse updateStatus(Long id, UpdateEventStatusRequest request) {
        EventStatus target = parseStatus(request.getStatus());
        securityUtils.assertPermission(permissionForStatus(target));

        Event event = loadInOrgScope(id);
        if (!event.getStatus().canTransitionTo(target)) {
            throw new IllegalArgumentException("Transition de statut invalide : "
                    + event.getStatus() + " → " + target);
        }
        EventStatus previous = event.getStatus();
        event.setStatus(target);
        event.setUpdatedBy(securityUtils.getCurrentUserId());
        Event saved = eventRepository.save(event);
        auditService.record("EVENT_STATUS", saved.getId(), "Event",
                securityUtils.getCurrentUserId(), saved.getOrganizationId(),
                "Changement de statut : " + previous + " → " + target);
        return loadFullResponse(saved);
    }

    /**
     * Suppression logique (soft-delete), même stratégie que les mariages.
     */
    @Transactional
    public void delete(Long id) {
        securityUtils.assertPermission("EVENT_DELETE");
        Event event = loadInOrgScope(id);
        event.setUpdatedBy(securityUtils.getCurrentUserId());
        event.softDelete();
        eventRepository.save(event);
        auditService.record("EVENT_DELETE", id, "Event",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Suppression logique de l'événement");
    }

    /**
     * Charge l'événement ET vérifie qu'il appartient au périmètre autorisé.
     * Réutilisée par le module sessions pour centraliser le contrôle d'accès.
     */
    public Event loadInOrgScope(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Événement non trouvé avec l'ID: " + id));
        securityUtils.assertOrganizationAccess(event.getOrganizationId());
        // Scoping agent : GESTIONNAIRE_INVITES / AGENT_ACCUEIL n'accèdent qu'aux
        // événements qui leur sont assignés (hérité de l'ancien WeddingService).
        securityUtils.assertWeddingAccess(event.getId());
        return event;
    }

    /**
     * Construit la réponse complète : socle + weddingDetails (si WEDDING) + sessions.
     */
    private EventResponse loadFullResponse(Event event) {
        WeddingDetails details = event.getType() == EventType.WEDDING
                ? weddingDetailsRepository.findByEventId(event.getId()).orElse(null)
                : null;
        List<EventSession> sessions = eventSessionRepository.findByEventId(event.getId());
        return eventMapper.toResponse(event, details, sessions);
    }

    /** Taille maximale de la photo d'événement (2 Mo). */
    private static final int IMAGE_MAX_BYTES = 2 * 1024 * 1024;

    /** Upload / remplace la photo de couverture de l'événement. */
    @Transactional
    public void setImage(Long id, byte[] image) {
        securityUtils.assertPermission("EVENT_UPDATE");
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Fichier image vide ou manquant");
        }
        if (image.length > IMAGE_MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 2 Mo)");
        }
        if (!isSupportedImage(image)) {
            throw new IllegalArgumentException("Format d'image non supporté (JPEG, PNG, GIF ou WebP attendu)");
        }
        Event event = loadInOrgScope(id);
        if (storageService.isEnabled()) {
            if (event.getImageKey() != null && !event.getImageKey().isBlank()) {
                storageService.delete(event.getImageKey());
            }
            String key = "events/" + id + "/" + System.currentTimeMillis() + extensionOf(image);
            storageService.upload(key, image, contentTypeOf(image));
            event.setImageKey(key);
            event.setImage(null);
        } else {
            event.setImage(image);
        }
        event.setUpdatedBy(securityUtils.getCurrentUserId());
        eventRepository.save(event);
        auditService.record("EVENT_UPDATE", id, "Event",
                securityUtils.getCurrentUserId(), event.getOrganizationId(), "Mise à jour de la photo de l'événement");
    }

    /** Photo de couverture (S3 d'abord, base en fallback) ; null si aucune. */
    @Transactional(readOnly = true)
    public byte[] getImage(Long id) {
        Event event = loadInOrgScope(id);
        if (event.getImageKey() != null && !event.getImageKey().isBlank()) {
            byte[] fromS3 = storageService.download(event.getImageKey());
            if (fromS3 != null) {
                return fromS3;
            }
        }
        return (event.getImage() == null || event.getImage().length == 0) ? null : event.getImage();
    }

    /** Supprime la photo de couverture. */
    @Transactional
    public void deleteImage(Long id) {
        securityUtils.assertPermission("EVENT_UPDATE");
        Event event = loadInOrgScope(id);
        if (event.getImageKey() != null && !event.getImageKey().isBlank()) {
            storageService.delete(event.getImageKey());
        }
        event.setImageKey(null);
        event.setImage(null);
        event.setUpdatedBy(securityUtils.getCurrentUserId());
        eventRepository.save(event);
    }

    private boolean isSupportedImage(byte[] b) {
        if (b.length < 12) return false;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return true;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return true;
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return true;
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private String extensionOf(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return ".jpg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return ".png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return ".gif";
        return ".webp";
    }

    private String contentTypeOf(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return "image/gif";
        return "image/webp";
    }

    /** Kinds autorisés pour les photos de la fiche mariage. */
    private static final java.util.Set<String> PHOTO_KINDS = java.util.Set.of("groom", "bride", "couple");

    /** Upload / remplace une photo de la fiche mariage (groom | bride | couple). */
    @Transactional
    public void setDetailPhoto(Long eventId, String kind, byte[] image) {
        securityUtils.assertPermission("EVENT_UPDATE");
        String k = kind == null ? "" : kind.toLowerCase();
        if (!PHOTO_KINDS.contains(k)) {
            throw new IllegalArgumentException("Type de photo invalide (groom, bride ou couple)");
        }
        validateImage(image);
        Event event = loadInOrgScope(eventId);
        WeddingDetails details = weddingDetailsRepository.findByEventId(eventId)
                .orElseGet(() -> WeddingDetails.builder().eventId(eventId).build());
        String oldKey = photoKeyOf(details, k);
        if (storageService.isEnabled()) {
            if (oldKey != null && !oldKey.isBlank() && !oldKey.startsWith("http")) {
                storageService.delete(oldKey);
            }
            String key = "events/" + eventId + "/" + k + "/" + System.currentTimeMillis() + extensionOf(image);
            storageService.upload(key, image, contentTypeOf(image));
            setPhotoKey(details, k, key);
            setImageBytes(details, k, null);
        } else {
            setImageBytes(details, k, image);
        }
        weddingDetailsRepository.save(details);
        auditService.record("EVENT_UPDATE", eventId, "Event",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Mise à jour de la photo " + k + " de la fiche mariage");
    }

    /** Photo de fiche mariage (S3 d'abord, base en fallback) ; null si aucune. Accès public. */
    @Transactional(readOnly = true)
    public byte[] getDetailPhoto(Long eventId, String kind) {
        String k = kind == null ? "" : kind.toLowerCase();
        if (!PHOTO_KINDS.contains(k)) {
            throw new IllegalArgumentException("Type de photo invalide (groom, bride ou couple)");
        }
        WeddingDetails details = weddingDetailsRepository.findByEventId(eventId).orElse(null);
        if (details == null) {
            return null;
        }
        String key = photoKeyOf(details, k);
        if (key != null && !key.isBlank() && !key.startsWith("http") && storageService.isEnabled()) {
            byte[] fromS3 = storageService.download(key);
            if (fromS3 != null) {
                return fromS3;
            }
        }
        byte[] bytes = imageBytesOf(details, k);
        return (bytes == null || bytes.length == 0) ? null : bytes;
    }

    private void validateImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Fichier image vide ou manquant");
        }
        if (image.length > IMAGE_MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 2 Mo)");
        }
        if (!isSupportedImage(image)) {
            throw new IllegalArgumentException("Format d'image non supporté (JPEG, PNG, GIF ou WebP attendu)");
        }
    }

    private String photoKeyOf(WeddingDetails d, String kind) {
        return switch (kind) {
            case "groom" -> d.getGroomPhotoUrl();
            case "bride" -> d.getBridePhotoUrl();
            default -> d.getCouplePhotoUrl();
        };
    }

    private void setPhotoKey(WeddingDetails d, String kind, String value) {
        switch (kind) {
            case "groom" -> d.setGroomPhotoUrl(value);
            case "bride" -> d.setBridePhotoUrl(value);
            default -> d.setCouplePhotoUrl(value);
        }
    }

    private byte[] imageBytesOf(WeddingDetails d, String kind) {
        return switch (kind) {
            case "groom" -> d.getGroomImage();
            case "bride" -> d.getBrideImage();
            default -> d.getCoupleImage();
        };
    }

    private void setImageBytes(WeddingDetails d, String kind, byte[] value) {
        switch (kind) {
            case "groom" -> d.setGroomImage(value);
            case "bride" -> d.setBrideImage(value);
            default -> d.setCoupleImage(value);
        }
    }

    private void upsertWeddingDetails(Long eventId, WeddingDetailsRequest request) {
        WeddingDetails details = weddingDetailsRepository.findByEventId(eventId)
                .orElseGet(() -> WeddingDetails.builder().eventId(eventId).build());
        if (request.getGroomFirstName() != null) details.setGroomFirstName(request.getGroomFirstName());
        if (request.getGroomLastName() != null) details.setGroomLastName(request.getGroomLastName());
        if (request.getBrideFirstName() != null) details.setBrideFirstName(request.getBrideFirstName());
        if (request.getBrideLastName() != null) details.setBrideLastName(request.getBrideLastName());
        if (request.getGroomPhotoUrl() != null) details.setGroomPhotoUrl(request.getGroomPhotoUrl());
        if (request.getBridePhotoUrl() != null) details.setBridePhotoUrl(request.getBridePhotoUrl());
        if (request.getCouplePhotoUrl() != null) details.setCouplePhotoUrl(request.getCouplePhotoUrl());
        if (request.getWelcomeMessage() != null) details.setWelcomeMessage(request.getWelcomeMessage());
        weddingDetailsRepository.save(details);
    }


    /**
     * Décision D2 : {@code weddingDetails} est requis pour WEDDING et interdit
     * pour les autres types.
     */
    private void validateWeddingDetailsPresence(EventType type, WeddingDetailsRequest details) {
        if (type == EventType.WEDDING && details == null) {
            throw new IllegalArgumentException("weddingDetails est requis pour un événement de type WEDDING");
        }
        if (type != EventType.WEDDING && details != null) {
            throw new IllegalArgumentException(
                    "weddingDetails n'est autorisé que pour un événement de type WEDDING");
        }
    }

    private void applyUpdate(Event event, UpdateEventRequest request) {
        if (request.getName() != null) event.setName(request.getName());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getMessage() != null) event.setMessage(request.getMessage());
        if (request.getEventDate() != null) event.setEventDate(request.getEventDate());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());
        if (request.getVenueName() != null) event.setVenueName(request.getVenueName());
        if (request.getVenueAddress() != null) event.setVenueAddress(request.getVenueAddress());
        if (request.getCity() != null) event.setCity(request.getCity());
        if (request.getCommune() != null) event.setCommune(request.getCommune());
        if (request.getCountry() != null) event.setCountry(request.getCountry());
        if (request.getLatitude() != null) event.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) event.setLongitude(request.getLongitude());
        if (request.getMapUrl() != null) event.setMapUrl(request.getMapUrl());
        if (request.getDisplayOrder() != null) event.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) event.setActive(request.getActive());
    }

    private Long resolveOrganizationForCreate(Long requestedOrganizationId) {
        if (securityUtils.isSuperAdmin()) {
            if (requestedOrganizationId == null) {
                throw new IllegalArgumentException("organizationId requis pour créer un événement (SUPER_ADMIN)");
            }
            organizationService.getOrganization(requestedOrganizationId); // vérifie l'existence
            return requestedOrganizationId;
        }
        return securityUtils.requireOrganizationId();
    }

    private EventStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Statut requis");
        }
        try {
            return EventStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Statut invalide : " + raw);
        }
    }

    /**
     * Permissions de statut réutilisées du module mariage (rôles déjà seedés).
     */
    private String permissionForStatus(EventStatus target) {
        return switch (target) {
            case PUBLISHED -> "WEDDING_PUBLISH";
            case ARCHIVED -> "WEDDING_ARCHIVE";
            default -> "WEDDING_UPDATE";
        };
    }

    private void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("L'heure de fin doit être strictement après l'heure de début");
        }
    }
}

