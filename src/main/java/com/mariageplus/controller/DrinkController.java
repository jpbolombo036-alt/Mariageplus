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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

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

    @PutMapping(value = "/{drinkId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader la photo d'une boisson (JPEG, PNG, GIF, WebP — max 2 Mo)")
    public ResponseEntity<?> uploadImage(@PathVariable Long weddingId,
                                         @PathVariable Long drinkId,
                                         @RequestParam("file") MultipartFile file) {
        try {
            drinkService.setImage(weddingId, drinkId, file.getBytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Impossible de lire le fichier"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{drinkId}/image")
    @Operation(summary = "Photo d'une boisson (public : carte visuelle du RSVP)")
    public ResponseEntity<byte[]> getImage(@PathVariable Long weddingId, @PathVariable Long drinkId) {
        byte[] image = drinkService.getImage(weddingId, drinkId);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectMediaType(image)))
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(image);
    }

    @DeleteMapping("/{drinkId}/image")
    @Operation(summary = "Supprimer la photo d'une boisson")
    public ResponseEntity<Void> deleteImage(@PathVariable Long weddingId, @PathVariable Long drinkId) {
        drinkService.deleteImage(weddingId, drinkId);
        return ResponseEntity.noContent().build();
    }

    private String detectMediaType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return "image/gif";
        return "image/webp";
    }
}
