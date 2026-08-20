package com.mariageplus.controller;

import com.mariageplus.dto.table.AssignGuestRequest;
import com.mariageplus.dto.table.CreateWeddingTableRequest;
import com.mariageplus.dto.table.TableAssignmentResponse;
import com.mariageplus.dto.table.UpdateWeddingTableRequest;
import com.mariageplus.dto.table.WeddingTableResponse;
import com.mariageplus.service.WeddingTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des tables d'un mariage (CRUD) et affectation d'un invité.
 * Isolées par organisation via weddingId → Wedding → Organization.
 */
@RestController
@RequestMapping("/api/weddings/{weddingId}/tables")
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Gestion des tables d'un mariage (isolées par organisation)")
public class WeddingTableController {

    private final WeddingTableService weddingTableService;

    @GetMapping
    @Operation(summary = "Liste des tables d'un mariage")
    public ResponseEntity<List<WeddingTableResponse>> list(@PathVariable Long weddingId) {
        return ResponseEntity.ok(weddingTableService.list(weddingId));
    }

    @PostMapping
    @Operation(summary = "Créer une table")
    public ResponseEntity<WeddingTableResponse> create(@PathVariable Long weddingId,
                                                       @Valid @RequestBody CreateWeddingTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(weddingTableService.create(weddingId, request));
    }

    @GetMapping("/{tableId}")
    @Operation(summary = "Table par ID")
    public ResponseEntity<WeddingTableResponse> getById(@PathVariable Long weddingId,
                                                        @PathVariable Long tableId) {
        return ResponseEntity.ok(weddingTableService.getById(weddingId, tableId));
    }

    @PutMapping("/{tableId}")
    @Operation(summary = "Modifier une table")
    public ResponseEntity<WeddingTableResponse> update(@PathVariable Long weddingId,
                                                       @PathVariable Long tableId,
                                                       @Valid @RequestBody UpdateWeddingTableRequest request) {
        return ResponseEntity.ok(weddingTableService.update(weddingId, tableId, request));
    }

    @DeleteMapping("/{tableId}")
    @Operation(summary = "Supprimer une table (refus si invités affectés)")
    public ResponseEntity<Void> delete(@PathVariable Long weddingId, @PathVariable Long tableId) {
        weddingTableService.delete(weddingId, tableId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tableId}/assignments")
    @Operation(summary = "Affecter un invité à une table")
    public ResponseEntity<TableAssignmentResponse> assign(@PathVariable Long weddingId,
                                                          @PathVariable Long tableId,
                                                          @Valid @RequestBody AssignGuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(weddingTableService.assign(weddingId, tableId, request));
    }
}