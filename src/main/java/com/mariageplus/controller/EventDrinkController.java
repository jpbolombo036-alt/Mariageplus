package com.mariageplus.controller;

import com.mariageplus.dto.drink.AvailableDrinkResponse;
import com.mariageplus.dto.drink.ToggleAvailableRequest;
import com.mariageplus.service.EventDrinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Disponibilité des boissons du CATALOGUE pour un événement.
 * L'organisateur (scope organisation vérifié côté service) déclare les
 * boissons que ses invités verront sur le RSVP public.
 */
@RestController
@RequestMapping({"/api/events/{weddingId}/available-drinks", "/api/weddings/{weddingId}/available-drinks"})
@RequiredArgsConstructor
@Tag(name = "Boissons disponibles", description = "Déclaration des boissons du catalogue disponibles pour un événement")
public class EventDrinkController {

    private final EventDrinkService eventDrinkService;

    @GetMapping
    @Operation(summary = "Catalogue + statut de disponibilité pour cet événement (organisateur)")
    public ResponseEntity<List<AvailableDrinkResponse>> list(@PathVariable Long weddingId) {
        return ResponseEntity.ok(eventDrinkService.listForEvent(weddingId));
    }

    @PutMapping
    @Operation(summary = "Déclarer / retirer la disponibilité d'une boisson du catalogue (organisateur)")
    public ResponseEntity<AvailableDrinkResponse> toggle(@PathVariable Long weddingId,
                                                         @Valid @RequestBody ToggleAvailableRequest request) {
        return ResponseEntity.ok(eventDrinkService.toggle(weddingId, request));
    }
}
