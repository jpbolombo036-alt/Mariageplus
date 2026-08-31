package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.drink.CreateDrinkRequest;
import com.mariageplus.dto.drink.DrinkResponse;
import com.mariageplus.dto.drink.UpdateDrinkRequest;
import com.mariageplus.service.DrinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/weddings/{weddingId}/drinks", "/api/events/{weddingId}/drinks"})
@RequiredArgsConstructor
@Tag(name = "Boissons", description = "Gestion des boissons d'un mariage (isolées par organisation)")
public class DrinkController {

    private final DrinkService drinkService;

    @GetMapping
    @Operation(summary = "Liste paginée des boissons d'un mariage")
    public ResponseEntity<PageResponse<DrinkResponse>> list(
            @PathVariable Long weddingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(drinkService.list(weddingId, page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer une boisson pour un mariage")
    public ResponseEntity<DrinkResponse> create(@PathVariable Long weddingId,
                                                @Valid @RequestBody CreateDrinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(drinkService.create(weddingId, request));
    }

    @GetMapping("/{drinkId}")
    @Operation(summary = "Boisson par ID (périmètre vérifié)")
    public ResponseEntity<DrinkResponse> getById(@PathVariable Long weddingId,
                                                 @PathVariable Long drinkId) {
        return ResponseEntity.ok(drinkService.getById(weddingId, drinkId));
    }

    @PutMapping("/{drinkId}")
    @Operation(summary = "Modifier une boisson (périmètre vérifié)")
    public ResponseEntity<DrinkResponse> update(@PathVariable Long weddingId,
                                                @PathVariable Long drinkId,
                                                @Valid @RequestBody UpdateDrinkRequest request) {
        return ResponseEntity.ok(drinkService.update(weddingId, drinkId, request));
    }

    @DeleteMapping("/{drinkId}")
    @Operation(summary = "Supprimer une boisson (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long weddingId, @PathVariable Long drinkId) {
        drinkService.delete(weddingId, drinkId);
        return ResponseEntity.noContent().build();
    }
}
