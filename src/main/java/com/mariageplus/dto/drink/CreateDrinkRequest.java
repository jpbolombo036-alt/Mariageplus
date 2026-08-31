package com.mariageplus.dto.drink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDrinkRequest {

    private String name;
    private String description;
    private Integer displayOrder;
}
