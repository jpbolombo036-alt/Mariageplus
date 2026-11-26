package com.mariageplus.dto.venue;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponse {

    private Long id;
    private String name;
    private String description;
    private String address;
    private String commune;
    private String city;
    private String country;
    private Double latitude;
    private Double longitude;
    private Integer capacitySeated;
    private Integer capacityStanding;
    private String amenities;
    private boolean active;

    /** Chemin de la photo (null si aucune). */
    private String imageUrl;

    private List<VenueRateResponse> rates;
}
