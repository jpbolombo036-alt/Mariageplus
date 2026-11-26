package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.venue.VenueClientRequest;
import com.mariageplus.dto.venue.VenueClientResponse;
import com.mariageplus.service.VenueBookingService;
import com.mariageplus.service.VenueClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Fiches clients des salles. */
@RestController
@RequestMapping("/api/venue-clients")
@RequiredArgsConstructor
@Tag(name = "Salles — clients", description = "Clients des salles (sans compte requis)")
public class VenueClientController {

    private final VenueClientService clientService;
    private final VenueBookingService bookingService;

    @GetMapping
    @Operation(summary = "Liste / recherche des clients (nom, téléphone, email)")
    public ResponseEntity<PageResponse<VenueClientResponse>> list(@RequestParam(required = false) String q,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(clientService.list(q, page, size));
    }

    @PostMapping
    @Operation(summary = "Créer un client (téléphone local 08… converti en +243…)")
    public ResponseEntity<VenueClientResponse> create(@Valid @RequestBody VenueClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Fiche client avec l'historique de ses réservations")
    public ResponseEntity<VenueClientResponse> get(@PathVariable Long clientId) {
        return ResponseEntity.ok(bookingService.clientDetail(clientId));
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "Modifier un client")
    public ResponseEntity<VenueClientResponse> update(@PathVariable Long clientId,
                                                      @Valid @RequestBody VenueClientRequest request) {
        return ResponseEntity.ok(clientService.update(clientId, request));
    }
}
