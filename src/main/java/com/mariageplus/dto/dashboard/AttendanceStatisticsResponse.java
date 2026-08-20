package com.mariageplus.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatisticsResponse {

    private long expected;
    private long checkedIn;
    private long remaining;
    private double checkInRate;
}