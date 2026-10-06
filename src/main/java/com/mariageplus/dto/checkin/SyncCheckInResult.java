package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncCheckInResult {

    private String qrToken;
    private String status;
    private String reason;
    private Long checkInId;
    private int totalAttendees;
    private int remainingAttendees;
}
