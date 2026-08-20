package com.mariageplus.mapper;

import com.mariageplus.dto.weddingevent.WeddingEventResponse;
import com.mariageplus.entity.WeddingEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WeddingEventMapper {

    @Mapping(target = "type", expression = "java(event.getType() == null ? null : event.getType().name())")
    WeddingEventResponse toResponse(WeddingEvent event);

    List<WeddingEventResponse> toResponseList(List<WeddingEvent> events);
}