package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.weddingevent.CreateWeddingEventRequest;
import com.mariageplus.dto.weddingevent.UpdateWeddingEventRequest;
import com.mariageplus.dto.weddingevent.WeddingEventResponse;
import com.mariageplus.service.WeddingEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/weddings/{weddingId}/events", "/api/events/{weddingId}/sessions-legacy"})
@RequiredArgsConstructor
@Tag(name = "Événements de mariage", description = "Gestion des événements d'un mariage (isolés par organisation)")
public class WeddingEventController {

    private final WeddingEventService weddingEventService;

    @GetMapping
    @Operation(summary = "Liste paginée des événements d'un mariage")
    public ResponseEntity<PageResponse<WeddingEventResponse>> list(
            @PathVariable Long weddingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "eventDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(weddingEventService.list(weddingId, page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer un événement pour un mariage")
    public ResponseEntity<WeddingEventResponse> create(@PathVariable Long weddingId,
                                                       @Valid @RequestBody CreateWeddingEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(weddingEventService.create(weddingId, request));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Événement par ID (périmètre vérifié)")
    public ResponseEntity<WeddingEventResponse> getById(@PathVariable Long weddingId,
                                                        @PathVariable Long eventId) {
        return ResponseEntity.ok(weddingEventService.getById(weddingId, eventId));
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Modifier un événement (périmètre vérifié)")
    public ResponseEntity<WeddingEventResponse> update(@PathVariable Long weddingId,
                                                       @PathVariable Long eventId,
                                                       @Valid @RequestBody UpdateWeddingEventRequest request) {
        return ResponseEntity.ok(weddingEventService.update(weddingId, eventId, request));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Supprimer un événement (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long weddingId, @PathVariable Long eventId) {
        weddingEventService.delete(weddingId, eventId);
        return ResponseEntity.noContent().build();
    }
}