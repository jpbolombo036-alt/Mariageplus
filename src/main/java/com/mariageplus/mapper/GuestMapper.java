package com.mariageplus.mapper;

import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.entity.Guest;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GuestMapper {

    GuestResponse toResponse(Guest guest);

    List<GuestResponse> toResponseList(List<Guest> guests);
}
