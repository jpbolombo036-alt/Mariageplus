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
public class OfflinePackTable {

    private Long tableId;
    private String name;
    private Integer capacity;
    private Integer assignedCount;
    private int remainingCapacity;
    private List<OfflinePackTableAssignment> assignments;
}
