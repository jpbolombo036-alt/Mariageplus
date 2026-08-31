package com.mariageplus.mapper;

import com.mariageplus.dto.drink.DrinkResponse;
import com.mariageplus.dto.drink.UpdateDrinkRequest;
import com.mariageplus.entity.Drink;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DrinkMapper {

    DrinkResponse toResponse(Drink drink);

    void updateFromRequest(UpdateDrinkRequest request, @MappingTarget Drink drink);
}
