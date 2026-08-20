package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.weddingevent.CreateWeddingEventRequest;
import com.mariageplus.dto.weddingevent.UpdateWeddingEventRequest;
import com.mariageplus.dto.weddingevent.WeddingEventResponse;
import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingEvent;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.WeddingEventMapper;
import com.mariageplus.repository.WeddingEventRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module événements d'un mariage. L'accès est conditionné par le mariage parent
 * (via {@link WeddingService#loadInOrgScope}) + la permission granulaire.
 */
@Service
@RequiredArgsConstructor
public class WeddingEventService {

    private final WeddingEventRepository weddingEventRepository;
    private final WeddingEventMapper weddingEventMapper;
    private final WeddingService weddingService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public WeddingEventResponse create(Long weddingId, CreateWeddingEventRequest request) {
        securityUtils.assertPermission("EVENT_CREATE");
        Wedding wedding = weddingService.loadInOrgScope(weddingId);
        validateTimes(request.getStartTime(), request.getEndTime());

        WeddingEvent event = WeddingEvent.builder()
                .weddingId(weddingId)
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
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
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();

        WeddingEvent saved = weddingEventRepository.save(event);
        auditService.record("WEDDING_EVENT_CREATE", saved.getId(), "WeddingEvent",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(),
                "Création de l'événement '" + saved.getName() + "'");
        return weddingEventMapper.toResponse(saved);
    }

    public PageResponse<WeddingEventResponse> list(Long weddingId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("EVENT_VIEW");
        weddingService.loadInOrgScope(weddingId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<WeddingEvent> eventPage = weddingEventRepository.findByWeddingId(weddingId, pageable);
        List<WeddingEventResponse> content = eventPage.getContent().stream()
                .map(weddingEventMapper::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, eventPage);
    }

    public WeddingEventResponse getById(Long weddingId, Long eventId) {
        securityUtils.assertPermission("EVENT_VIEW");
        weddingService.loadInOrgScope(weddingId);
        WeddingEvent event = loadEvent(weddingId, eventId);
        return weddingEventMapper.toResponse(event);
    }
        @Transactional
    public WeddingEventResponse update(Long weddingId, Long eventId, UpdateWeddingEventRequest request) {
        securityUtils.assertPermission("EVENT_UPDATE");
        Wedding wedding = weddingService.loadInOrgScope(weddingId);
        WeddingEvent event = loadEvent(weddingId, eventId);
        applyUpdate(event, request);
        validateTimes(event.getStartTime(), event.getEndTime());

        WeddingEvent saved = weddingEventRepository.save(event);
        auditService.record("WEDDING_EVENT_UPDATE", saved.getId(), "WeddingEvent",
                securityUtils.getCurrentUserId(), wedding.getOrganizationId(),
                "Modification de l'événement '" + saved.getName() + "'");
        return weddingEventMapper.toResponse(saved);
    }

    /**
     * Suppression logique de l'événement (soft-delete).
     */
    @Transactional
    public void delete(Long weddingId, Long eventId) {
        securityUtils.assertPermission("EVENT_DELETE");
        weddingService.loadInOrgScope(weddingId);
        WeddingEvent event = loadEvent(weddingId, eventId);
        event.softDelete();
        weddingEventRepository.save(event);
    }

    private WeddingEvent loadEvent(Long weddingId, Long eventId) {
        return weddingEventRepository.findByIdAndWeddingId(eventId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Événement non trouvé avec l'ID: " + eventId + " pour le mariage " + weddingId));
    }

    private void applyUpdate(WeddingEvent event, UpdateWeddingEventRequest request) {
        if (request.getName() != null) event.setName(request.getName());
        if (request.getType() != null) event.setType(request.getType());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
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

    private void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("L'heure de fin doit être strictement après l'heure de début");
        }
    }
}