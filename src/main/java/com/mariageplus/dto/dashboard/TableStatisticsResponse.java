package com.mariageplus.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableStatisticsResponse {

    private long total;
    private long capacity;
    private long assignedGuests;
    private long remainingCapacity;
}