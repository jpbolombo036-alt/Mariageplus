package com.mariageplus.controller;

import com.mariageplus.dto.drink.CatalogDrinkResponse;
import com.mariageplus.dto.drink.CreateCatalogDrinkRequest;
import com.mariageplus.service.DrinkCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * CATALOGUE GLOBAL des boissons — écriture réservée au SUPER_ADMIN.
 * La photo (GET /{id}/image) est publique : elle est affichée sur les
 * cartes RSVP des invités.
 */
@RestController
@RequestMapping("/api/admin/drink-catalog")
@RequiredArgsConstructor
@Tag(name = "Catalogue boissons (SUPER_ADMIN)", description = "Catalogue global des boissons, géré par le SUPER_ADMIN")
public class DrinkCatalogAdminController {

    private final DrinkCatalogService catalogService;

    @GetMapping
    @Operation(summary = "Catalogue complet (SUPER_ADMIN)")
    public ResponseEntity<List<CatalogDrinkResponse>> list() {
        return ResponseEntity.ok(catalogService.list());
    }

    @PostMapping
    @Operation(summary = "Créer une boisson de catalogue (SUPER_ADMIN)")
    public ResponseEntity<CatalogDrinkResponse> create(@Valid @RequestBody CreateCatalogDrinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une boisson de catalogue (SUPER_ADMIN)")
    public ResponseEntity<CatalogDrinkResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody CreateCatalogDrinkRequest request) {
        return ResponseEntity.ok(catalogService.update(id, request));
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Activer / désactiver une boisson du catalogue (SUPER_ADMIN)")
    public ResponseEntity<CatalogDrinkResponse> setActive(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        boolean active = Boolean.parseBoolean(String.valueOf(body == null ? null : body.get("active")));
        return ResponseEntity.ok(catalogService.setActive(id, active));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une boisson du catalogue (SUPER_ADMIN, suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader / remplacer la photo (SUPER_ADMIN — JPEG, PNG, GIF, WebP, max 2 Mo)")
    public ResponseEntity<Void> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        catalogService.uploadImage(id, file);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    @Operation(summary = "Photo de la boisson (public — carte RSVP des invités)")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        byte[] image = catalogService.getImage(id);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectMediaType(image)))
                .cacheControl(org.springframework.http.CacheControl.maxAge(5, java.util.concurrent.TimeUnit.MINUTES).cachePublic())
                .body(image);
    }

    @DeleteMapping("/{id}/image")
    @Operation(summary = "Supprimer la photo (SUPER_ADMIN)")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        catalogService.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    private String detectMediaType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return "image/gif";
        return "image/webp";
    }
}
