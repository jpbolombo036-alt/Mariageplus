package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.event.CreateEventRequest;
import com.mariageplus.dto.event.EventResponse;
import com.mariageplus.dto.event.UpdateEventRequest;
import com.mariageplus.dto.event.UpdateEventStatusRequest;
import com.mariageplus.entity.EventType;
import com.mariageplus.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Événements (nouvelle racine métier unifiée — Phase 1, coexistence avec
 * /api/weddings). Le type (mariage, collation, anniversaire...) porte les
 * champs spécifiques : seul WEDDING accepte {@code weddingDetails}.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Événements", description = "Gestion des événements (CRUD + statut, isolés par organisation)")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Liste paginée des événements (filtre optionnel par type, périmètre de l'utilisateur, global pour SUPER_ADMIN)")
    public ResponseEntity<PageResponse<EventResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) EventType type) {
        return ResponseEntity.ok(eventService.list(page, size, sortBy, sortDir, type));
    }

    @PostMapping
    @Operation(summary = "Créer un événement (weddingDetails requis si type=WEDDING, interdit sinon)")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Événement par ID (périmètre vérifié, inclut weddingDetails et sessions)")
    public ResponseEntity<EventResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un événement (périmètre vérifié)")
    public ResponseEntity<EventResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un événement (transition validée, permission selon la cible)")
    public ResponseEntity<EventResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateEventStatusRequest request) {
        return ResponseEntity.ok(eventService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un événement (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
