package com.mariageplus.dto.drink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrinkResponse {

    private Long id;
    private Long weddingId;
    private String name;
    private String description;
    private Integer displayOrder;
    private boolean active;
}
