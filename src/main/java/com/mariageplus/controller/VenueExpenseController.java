package com.mariageplus.controller;

import com.mariageplus.dto.venue.VenueExpenseListResponse;
import com.mariageplus.dto.venue.VenueExpenseRequest;
import com.mariageplus.dto.venue.VenueExpenseResponse;
import com.mariageplus.entity.VenueExpenseCategory;
import com.mariageplus.service.VenueExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Dépenses de salle : saisie (gérant, caissier), modification et annulation (gérant). */
@RestController
@RequestMapping("/api/venues/expenses")
@RequiredArgsConstructor
@Tag(name = "Salles — dépenses", description = "Sorties d'argent de la salle (module Salles)")
public class VenueExpenseController {

    private final VenueExpenseService expenseService;

    @GetMapping
    @Operation(summary = "Dépenses de la période (le caissier ne voit que les siennes), total par devise et par poste")
    public ResponseEntity<VenueExpenseListResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) VenueExpenseCategory category) {
        return ResponseEntity.ok(expenseService.list(from, to, venueId, category));
    }

    @PostMapping
    @Operation(summary = "Enregistrer une dépense")
    public ResponseEntity<VenueExpenseResponse> create(@Valid @RequestBody VenueExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(request));
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Modifier une dépense (gérant)")
    public ResponseEntity<VenueExpenseResponse> update(@PathVariable Long expenseId,
                                                       @Valid @RequestBody VenueExpenseRequest request) {
        return ResponseEntity.ok(expenseService.update(expenseId, request));
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Annuler une dépense (gérant) ; le motif est gardé dans l'historique")
    public ResponseEntity<Void> cancel(@PathVariable Long expenseId, @RequestParam(required = false) String reason) {
        expenseService.cancel(expenseId, reason);
        return ResponseEntity.noContent().build();
    }
}
