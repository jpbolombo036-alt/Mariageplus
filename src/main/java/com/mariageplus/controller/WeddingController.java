package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.UpdateWeddingRequest;
import com.mariageplus.dto.wedding.UpdateWeddingStatusRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.service.WeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weddings")
@RequiredArgsConstructor
@Tag(name = "Mariages", description = "Gestion des mariages (CRUD + statut, isolés par organisation)")
public class WeddingController {

    private final WeddingService weddingService;

    @GetMapping
    @Operation(summary = "Liste paginée des mariages (périmètre de l'utilisateur, global pour SUPER_ADMIN)")
    public ResponseEntity<PageResponse<WeddingResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(weddingService.list(page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer un mariage (dans le périmètre de l'utilisateur)")
    public ResponseEntity<WeddingResponse> create(@Valid @RequestBody CreateWeddingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weddingService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Mariage par ID (périmètre vérifié)")
    public ResponseEntity<WeddingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(weddingService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un mariage (périmètre vérifié)")
    public ResponseEntity<WeddingResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateWeddingRequest request) {
        return ResponseEntity.ok(weddingService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un mariage (transition validée, permission selon la cible)")
    public ResponseEntity<WeddingResponse> updateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateWeddingStatusRequest request) {
        return ResponseEntity.ok(weddingService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un mariage (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        weddingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}