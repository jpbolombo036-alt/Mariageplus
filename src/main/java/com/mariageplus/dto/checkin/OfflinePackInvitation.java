package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePackInvitation {

    private String publicToken;
    private String invitationCode;
    private Long guestId;
    private String guestName;
    private String invitationStatus;
    private String rsvpStatus;
    private int expectedAttendees;
    private int checkedInAttendees;
    private int remainingAttendees;
    private boolean canCheckIn;
    private String tableName;
    private String drinkChoice;
    private boolean hasCard;
    private String phone;
    private String email;
}
