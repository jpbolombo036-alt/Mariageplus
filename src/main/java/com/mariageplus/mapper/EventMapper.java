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
    EventResponse toResponse(Event event);

    default EventResponse toResponse(Event event, WeddingDetails details, List<EventSession> sessions) {
        EventResponse response = toResponse(event);
        response.setWeddingDetails(details == null ? null : toDetailsResponse(details));
        response.setSessions(sessions == null ? null : toSessionResponseList(sessions));
        return response;
    }

    @Mapping(target = "displayName", expression = "java(details.getDisplayName())")
    WeddingDetailsResponse toDetailsResponse(WeddingDetails details);

    @Mapping(target = "type", expression = "java(session.getType() == null ? null : session.getType().name())")
    EventSessionResponse toSessionResponse(EventSession session);

    List<EventSessionResponse> toSessionResponseList(List<EventSession> sessions);
}
