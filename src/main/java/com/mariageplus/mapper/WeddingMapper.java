package com.mariageplus.mapper;

import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.entity.Wedding;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WeddingMapper {

    @Mapping(target = "status", expression = "java(wedding.getStatus() == null ? null : wedding.getStatus().name())")
    @Mapping(target = "displayName", expression = "java(wedding.getDisplayName())")
    WeddingResponse toResponse(Wedding wedding);

    List<WeddingResponse> toResponseList(List<Wedding> weddings);
}