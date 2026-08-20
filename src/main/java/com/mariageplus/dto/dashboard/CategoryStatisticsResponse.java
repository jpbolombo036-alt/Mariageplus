package com.mariageplus.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatisticsResponse {

    private Long categoryId;
    private String name;
    private long totalGuests;
    private long accepted;
    private long declined;
    private long pending;
    private long expectedAttendees;
}