package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePackResponse {

    private Long eventId;
    private String eventName;
    private String eventDate;
    private String eventTime;
    private String eventVenue;
    private String dressColors;
    private List<OfflinePackInvitation> invitations;
    private List<OfflinePackTable> tables;
    private long generatedAt;
}
