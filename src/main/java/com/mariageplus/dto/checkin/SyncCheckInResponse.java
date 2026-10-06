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
public class SyncCheckInResponse {

    private int processed;
    private int accepted;
    private int rejected;
    private List<SyncCheckInResult> results;
}
