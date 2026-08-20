package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.guestcategory.CreateGuestCategoryRequest;
import com.mariageplus.dto.guestcategory.GuestCategoryResponse;
import com.mariageplus.dto.guestcategory.UpdateGuestCategoryRequest;
import com.mariageplus.service.GuestCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weddings/{weddingId}/guest-categories")
@RequiredArgsConstructor
@Tag(name = "Catégories d'invités", description = "Gestion des catégories d'invités d'un mariage (isolées par organisation)")
public class GuestCategoryController {

    private final GuestCategoryService guestCategoryService;

    @GetMapping
    @Operation(summary = "Liste paginée des catégories d'un mariage")
    public ResponseEntity<PageResponse<GuestCategoryResponse>> list(
            @PathVariable Long weddingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(guestCategoryService.list(weddingId, page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer une catégorie pour un mariage")
    public ResponseEntity<GuestCategoryResponse> create(@PathVariable Long weddingId,
                                                        @Valid @RequestBody CreateGuestCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guestCategoryService.create(weddingId, request));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Catégorie par ID (périmètre vérifié)")
    public ResponseEntity<GuestCategoryResponse> getById(@PathVariable Long weddingId,
                                                         @PathVariable Long categoryId) {
        return ResponseEntity.ok(guestCategoryService.getById(weddingId, categoryId));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Modifier une catégorie (périmètre vérifié)")
    public ResponseEntity<GuestCategoryResponse> update(@PathVariable Long weddingId,
                                                        @PathVariable Long categoryId,
                                                        @Valid @RequestBody UpdateGuestCategoryRequest request) {
        return ResponseEntity.ok(guestCategoryService.update(weddingId, categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Supprimer une catégorie (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long weddingId, @PathVariable Long categoryId) {
        guestCategoryService.delete(weddingId, categoryId);
        return ResponseEntity.noContent().build();
    }
}
