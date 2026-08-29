package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.event.CreateEventSessionRequest;
import com.mariageplus.dto.event.EventSessionResponse;
import com.mariageplus.dto.event.UpdateEventSessionRequest;
import com.mariageplus.service.EventSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sessions (sous-étapes) d'un événement — ex-événements de mariage.
 * S'applique à tous les types (cérémonie civile, religieuse, réception,
 * vin d'honneur, soirée...).
 */
@RestController
@RequestMapping("/api/events/{eventId}/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions d'événement", description = "Gestion des sous-étapes d'un événement (isolées par organisation)")
public class EventSessionController {

    private final EventSessionService eventSessionService;

    @GetMapping
    @Operation(summary = "Liste paginée des sessions d'un événement")
    public ResponseEntity<PageResponse<EventSessionResponse>> list(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "sessionDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(eventSessionService.list(eventId, page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer une session pour un événement")
    public ResponseEntity<EventSessionResponse> create(@PathVariable Long eventId,
                                                       @Valid @RequestBody CreateEventSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventSessionService.create(eventId, request));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Session par ID (périmètre vérifié)")
    public ResponseEntity<EventSessionResponse> getById(@PathVariable Long eventId,
                                                        @PathVariable Long sessionId) {
        return ResponseEntity.ok(eventSessionService.getById(eventId, sessionId));
    }

    @PutMapping("/{sessionId}")
    @Operation(summary = "Modifier une session (périmètre vérifié)")
    public ResponseEntity<EventSessionResponse> update(@PathVariable Long eventId,
                                                       @PathVariable Long sessionId,
                                                       @Valid @RequestBody UpdateEventSessionRequest request) {
        return ResponseEntity.ok(eventSessionService.update(eventId, sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Supprimer une session (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long eventId, @PathVariable Long sessionId) {
        eventSessionService.delete(eventId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
