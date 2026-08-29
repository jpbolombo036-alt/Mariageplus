package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.event.CreateEventSessionRequest;
import com.mariageplus.dto.event.EventSessionResponse;
import com.mariageplus.dto.event.UpdateEventSessionRequest;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventSession;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.EventMapper;
import com.mariageplus.repository.EventSessionRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * Module sessions (sous-étapes) d'un événement. L'accès est conditionné par
 * l'événement parent (via {@link EventService#loadInOrgScope}) + permission
 * granulaire. Mêmes règles que l'ancien module {@code WeddingEventService}.
 */
@Service
@RequiredArgsConstructor
public class EventSessionService {

    private final EventSessionRepository eventSessionRepository;
    private final EventMapper eventMapper;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public EventSessionResponse create(Long eventId, CreateEventSessionRequest request) {
        securityUtils.assertPermission("EVENT_CREATE");
        Event event = eventService.loadInOrgScope(eventId);
        validateTimes(request.getStartTime(), request.getEndTime());

        EventSession session = EventSession.builder()
                .eventId(eventId)
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .sessionDate(request.getSessionDate())
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

        EventSession saved = eventSessionRepository.save(session);
        auditService.record("EVENT_SESSION_CREATE", saved.getId(), "EventSession",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création de la session '" + saved.getName() + "'");
        return eventMapper.toSessionResponse(saved);
    }

    public PageResponse<EventSessionResponse> list(Long eventId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("EVENT_VIEW");
        eventService.loadInOrgScope(eventId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<EventSession> sessionPage = eventSessionRepository.findByEventId(eventId, pageable);
        return PageResponse.of(sessionPage.getContent().stream()
                .map(eventMapper::toSessionResponse).toList(), sessionPage);
    }

    public EventSessionResponse getById(Long eventId, Long sessionId) {
        securityUtils.assertPermission("EVENT_VIEW");
        eventService.loadInOrgScope(eventId);
        EventSession session = loadSession(eventId, sessionId);
        return eventMapper.toSessionResponse(session);
    }

    @Transactional
    public EventSessionResponse update(Long eventId, Long sessionId, UpdateEventSessionRequest request) {
        securityUtils.assertPermission("EVENT_UPDATE");
        Event event = eventService.loadInOrgScope(eventId);
        EventSession session = loadSession(eventId, sessionId);
        applyUpdate(session, request);
        validateTimes(session.getStartTime(), session.getEndTime());

        EventSession saved = eventSessionRepository.save(session);
        auditService.record("EVENT_SESSION_UPDATE", saved.getId(), "EventSession",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Modification de la session '" + saved.getName() + "'");
        return eventMapper.toSessionResponse(saved);
    }


    private void applyUpdate(EventSession session, UpdateEventSessionRequest request) {
        if (request.getName() != null) session.setName(request.getName());
        if (request.getType() != null) session.setType(request.getType());
        if (request.getDescription() != null) session.setDescription(request.getDescription());
        if (request.getSessionDate() != null) session.setSessionDate(request.getSessionDate());
        if (request.getStartTime() != null) session.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) session.setEndTime(request.getEndTime());
        if (request.getVenueName() != null) session.setVenueName(request.getVenueName());
        if (request.getVenueAddress() != null) session.setVenueAddress(request.getVenueAddress());
        if (request.getCity() != null) session.setCity(request.getCity());
        if (request.getCommune() != null) session.setCommune(request.getCommune());
        if (request.getCountry() != null) session.setCountry(request.getCountry());
        if (request.getLatitude() != null) session.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) session.setLongitude(request.getLongitude());
        if (request.getMapUrl() != null) session.setMapUrl(request.getMapUrl());
        if (request.getDisplayOrder() != null) session.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) session.setActive(request.getActive());
    }

    private void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("L'heure de fin doit être strictement après l'heure de début");
        }
    }

    /**
     * Suppression logique de la session (soft-delete).
     */
    @Transactional
    public void delete(Long eventId, Long sessionId) {
        securityUtils.assertPermission("EVENT_DELETE");
        eventService.loadInOrgScope(eventId);
        EventSession session = loadSession(eventId, sessionId);
        session.softDelete();
        eventSessionRepository.save(session);
    }

    private EventSession loadSession(Long eventId, Long sessionId) {
        return eventSessionRepository.findByIdAndEventId(sessionId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session non trouvée avec l'ID: " + sessionId + " pour l'événement " + eventId));
    }
}
