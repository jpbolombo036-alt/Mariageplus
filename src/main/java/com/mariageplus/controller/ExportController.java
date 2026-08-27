package com.mariageplus.controller;

import com.mariageplus.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weddings/{weddingId}/export")
@RequiredArgsConstructor
@Tag(name = "Exports", description = "Export CSV et PDF des données d'un mariage")
public class ExportController {

    private final ExportService exportService;

    @GetMapping(value = "/guests/csv", produces = "text/csv")
    @Operation(summary = "Exporter les invités au format CSV")
    public ResponseEntity<byte[]> exportGuestsCsv(@PathVariable Long weddingId) {
        byte[] bytes = exportService.exportGuestsCsv(weddingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"guests.csv\"")
                .body(bytes);
    }

    @GetMapping(value = "/invitations/csv", produces = "text/csv")
    @Operation(summary = "Exporter les invitations au format CSV")
    public ResponseEntity<byte[]> exportInvitationsCsv(@PathVariable Long weddingId) {
        byte[] bytes = exportService.exportInvitationsCsv(weddingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invitations.csv\"")
                .body(bytes);
    }

    @GetMapping(value = "/rsvps/csv", produces = "text/csv")
    @Operation(summary = "Exporter les réponses RSVP au format CSV")
    public ResponseEntity<byte[]> exportRsvpsCsv(@PathVariable Long weddingId) {
        byte[] bytes = exportService.exportRsvpsCsv(weddingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rsvps.csv\"")
                .body(bytes);
    }

    @GetMapping(value = "/tables/csv", produces = "text/csv")
    @Operation(summary = "Exporter les tables et affectations au format CSV")
    public ResponseEntity<byte[]> exportTablesCsv(@PathVariable Long weddingId) {
        byte[] bytes = exportService.exportTablesCsv(weddingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tables.csv\"")
                .body(bytes);
    }

    @GetMapping(value = "/report/pdf", produces = "application/pdf")
    @Operation(summary = "Exporter le rapport de synthèse au format PDF")
    public ResponseEntity<byte[]> exportReportPdf(@PathVariable Long weddingId) {
        byte[] bytes = exportService.exportDashboardPdf(weddingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .body(bytes);
    }
}
