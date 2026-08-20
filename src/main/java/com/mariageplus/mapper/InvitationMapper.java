package com.mariageplus.mapper;

import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.entity.Invitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InvitationMapper {

    @Mapping(target = "status", expression = "java(invitation.getStatus() == null ? null : invitation.getStatus().name())")
    InvitationResponse toResponse(Invitation invitation);

    List<InvitationResponse> toResponseList(List<Invitation> invitations);
}
