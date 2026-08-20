package com.mariageplus.mapper;

import com.mariageplus.dto.guestcategory.GuestCategoryResponse;
import com.mariageplus.entity.GuestCategory;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GuestCategoryMapper {

    GuestCategoryResponse toResponse(GuestCategory category);

    List<GuestCategoryResponse> toResponseList(List<GuestCategory> categories);
}
