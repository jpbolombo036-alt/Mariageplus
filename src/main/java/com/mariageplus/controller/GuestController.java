package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.guest.CreateGuestRequest;
import com.mariageplus.dto.guest.GuestImportResponse;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.dto.guest.UpdateGuestRequest;
import com.mariageplus.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/weddings/{weddingId}/guests", "/api/events/{weddingId}/guests"})
@RequiredArgsConstructor
@Tag(name = "Invités", description = "Gestion des invités d'un mariage (isolés par organisation)")
public class GuestController {

    private final GuestService guestService;

    @GetMapping
    @Operation(summary = "Liste paginée des invités d'un mariage")
    public ResponseEntity<PageResponse<GuestResponse>> list(
            @PathVariable Long weddingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(guestService.list(weddingId, page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer un invité pour un mariage")
    public ResponseEntity<GuestResponse> create(@PathVariable Long weddingId,
                                                @Valid @RequestBody CreateGuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.create(weddingId, request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer des invités depuis un CSV (erreurs rapportées par ligne)")
    public ResponseEntity<GuestImportResponse> importCsv(@PathVariable Long weddingId,
                                                         @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(guestService.importCsv(weddingId, file));
    }

    @GetMapping("/{guestId}")
    @Operation(summary = "Invité par ID (périmètre vérifié)")
    public ResponseEntity<GuestResponse> getById(@PathVariable Long weddingId, @PathVariable Long guestId) {
        return ResponseEntity.ok(guestService.getById(weddingId, guestId));
    }

    @PutMapping("/{guestId}")
    @Operation(summary = "Modifier un invité (périmètre vérifié)")
    public ResponseEntity<GuestResponse> update(@PathVariable Long weddingId,
                                                @PathVariable Long guestId,
                                                @Valid @RequestBody UpdateGuestRequest request) {
        return ResponseEntity.ok(guestService.update(weddingId, guestId, request));
    }

    @DeleteMapping("/{guestId}")
    @Operation(summary = "Supprimer un invité (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long weddingId, @PathVariable Long guestId) {
        guestService.delete(weddingId, guestId);
        return ResponseEntity.noContent().build();
    }
}
