package com.mariageplus.dto.weddingevent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeddingEventResponse {

    private Long id;
    private Long weddingId;
    private String type;
    private String name;
    private String description;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venueName;
    private String venueAddress;
    private String city;
    private String commune;
    private String country;
    private Double latitude;
    private Double longitude;
    private String mapUrl;
    private Integer displayOrder;
    private boolean active;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
