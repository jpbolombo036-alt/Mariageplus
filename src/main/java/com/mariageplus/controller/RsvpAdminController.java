package com.mariageplus.controller;

import com.mariageplus.dto.guest.RsvpSummaryResponse;
import com.mariageplus.service.RsvpQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADDITIF (GESTIONNAIRE_INVITES) : consultation des réponses RSVP d'un mariage.
 * Endpoint en lecture seule ; aucune route existante n'est modifiée.
 */
@RestController
@RequestMapping({"/api/weddings/{weddingId}/rsvps", "/api/events/{weddingId}/rsvps"})
@RequiredArgsConstructor
@Tag(name = "Réponses RSVP", description = "Réponses RSVP par invité (isolées par organisation)")
public class RsvpAdminController {

    private final RsvpQueryService rsvpQueryService;

    @GetMapping
    @Operation(summary = "Liste des réponses RSVP du mariage (invitation + invité + statut)")
    public ResponseEntity<List<RsvpSummaryResponse>> list(@PathVariable Long weddingId) {
        return ResponseEntity.ok(rsvpQueryService.listForWedding(weddingId));
    }
}