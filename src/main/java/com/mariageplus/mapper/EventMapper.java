package com.mariageplus.mapper;

import com.mariageplus.dto.event.EventResponse;
import com.mariageplus.dto.event.EventSessionResponse;
import com.mariageplus.dto.event.WeddingDetailsResponse;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventSession;
import com.mariageplus.entity.WeddingDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mappers MapStruct du module événements (racine unifiée).
 * Les enums sont convertis en String pour exposer un contrat d'API stable.
 */
@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "type", expression = "java(event.getType() == null ? null : event.getType().name())")
    @Mapping(target = "status", expression = "java(event.getStatus() == null ? null : event.getStatus().name())")
    @Mapping(target = "hasImage", expression = "java((event.getImageKey() != null && !event.getImageKey().isBlank()) || (event.getImage() != null && event.getImage().length > 0))")
    EventResponse toResponse(Event event);

    default EventResponse toResponse(Event event, WeddingDetails details, List<EventSession> sessions) {
        EventResponse response = toResponse(event);
        response.setWeddingDetails(details == null ? null : toDetailsResponse(event.getId(), details));
        response.setSessions(sessions == null ? null : toSessionResponseList(sessions));
        return response;
    }

    @Mapping(target = "displayName", expression = "java(details.getDisplayName())")
    @Mapping(target = "groomPhotoUrl", expression = "java(resolvePhotoUrl(eventId, details.getGroomPhotoUrl(), \"groom\"))")
    @Mapping(target = "bridePhotoUrl", expression = "java(resolvePhotoUrl(eventId, details.getBridePhotoUrl(), \"bride\"))")
    @Mapping(target = "couplePhotoUrl", expression = "java(resolvePhotoUrl(eventId, details.getCouplePhotoUrl(), \"couple\"))")
    WeddingDetailsResponse toDetailsResponse(Long eventId, WeddingDetails details);

    /** Transforme une clé S3 stockée en URL d'API affichable (les URL http restent telles quelles). */
    default String resolvePhotoUrl(Long eventId, String stored, String kind) {
        if (stored == null || stored.isBlank()) return null;
        if (stored.startsWith("http://") || stored.startsWith("https://")) return stored;
        return eventId == null ? null : "/api/events/" + eventId + "/photos/" + kind;
    }

    @Mapping(target = "type", expression = "java(session.getType() == null ? null : session.getType().name())")
    EventSessionResponse toSessionResponse(EventSession session);

    List<EventSessionResponse> toSessionResponseList(List<EventSession> sessions);
}
