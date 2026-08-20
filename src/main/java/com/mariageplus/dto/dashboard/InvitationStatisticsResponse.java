package com.mariageplus.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationStatisticsResponse {

    private long total;
    private long accepted;
    private long declined;
    private long pending;
    private double responseRate;
}