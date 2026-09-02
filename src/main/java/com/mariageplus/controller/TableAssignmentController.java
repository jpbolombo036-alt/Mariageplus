package com.mariageplus.controller;

import com.mariageplus.dto.table.MoveGuestRequest;
import com.mariageplus.dto.table.TableAssignmentResponse;
import com.mariageplus.service.WeddingTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consultation, déplacement et retrait des affectations table/invité.
 */
@RestController
@RequestMapping({"/api/weddings/{weddingId}/assignments", "/api/events/{weddingId}/assignments"})
@RequiredArgsConstructor
@Tag(name = "Affectations de tables", description = "Consulter, déplacer ou retirer un invité d'une table")
public class TableAssignmentController {

    private final WeddingTableService weddingTableService;

    @GetMapping
    @Operation(summary = "Liste des affectations table/invité d'un mariage")
    public ResponseEntity<List<TableAssignmentResponse>> list(@PathVariable Long weddingId) {
        return ResponseEntity.ok(weddingTableService.listAssignments(weddingId));
    }

    @PutMapping("/{assignmentId}")
    @Operation(summary = "Déplacer un invité vers une autre table")
    public ResponseEntity<TableAssignmentResponse> move(@PathVariable Long weddingId,
                                                        @PathVariable Long assignmentId,
                                                        @Valid @RequestBody MoveGuestRequest request) {
        return ResponseEntity.ok(weddingTableService.move(weddingId, assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}")
    @Operation(summary = "Retirer un invité d'une table")
    public ResponseEntity<Void> remove(@PathVariable Long weddingId,
                                       @PathVariable Long assignmentId) {
        weddingTableService.remove(weddingId, assignmentId);
        return ResponseEntity.noContent().build();
    }
}