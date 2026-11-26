package com.mariageplus.controller;

import com.mariageplus.dto.venue.*;
import com.mariageplus.service.VenueBookingService;
import com.mariageplus.service.VenueModuleGuard;
import com.mariageplus.service.VenueReportService;
import com.mariageplus.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Module « Salles » : salles, tarifs, calendrier, tableau de bord et export. */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Tag(name = "Salles", description = "Gestion des salles de fête (module activé par le SUPER_ADMIN)")
public class VenueController {

    private final VenueService venueService;
    private final VenueBookingService bookingService;
    private final VenueReportService reportService;
    private final VenueModuleGuard guard;

    @GetMapping("/module-status")
    @Operation(summary = "Le module Salles est-il activé pour mon organisation ?")
    public ResponseEntity<Map<String, Boolean>> moduleStatus() {
        return ResponseEntity.ok(Map.of("enabled", guard.isEnabledForCurrentUser()));
    }

    @GetMapping
    @Operation(summary = "Salles de mon organisation")
    public ResponseEntity<List<VenueResponse>> list() {
        return ResponseEntity.ok(venueService.list());
    }

    @PostMapping
    @Operation(summary = "Créer une salle")
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.create(request));
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Détail d'une salle et ses tarifs")
    public ResponseEntity<VenueResponse> get(@PathVariable Long venueId) {
        return ResponseEntity.ok(venueService.get(venueId));
    }

    @PutMapping("/{venueId}")
    @Operation(summary = "Modifier une salle")
    public ResponseEntity<VenueResponse> update(@PathVariable Long venueId, @Valid @RequestBody VenueRequest request) {
        return ResponseEntity.ok(venueService.update(venueId, request));
    }

    @DeleteMapping("/{venueId}")
    @Operation(summary = "Supprimer une salle (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long venueId) {
        venueService.delete(venueId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{venueId}/rates")
    @Operation(summary = "Ajouter un tarif (USD ou CDF)")
    public ResponseEntity<VenueRateResponse> addRate(@PathVariable Long venueId, @Valid @RequestBody VenueRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.addRate(venueId, request));
    }

    @PutMapping("/{venueId}/rates/{rateId}")
    @Operation(summary = "Modifier un tarif")
    public ResponseEntity<VenueRateResponse> updateRate(@PathVariable Long venueId, @PathVariable Long rateId,
                                                        @Valid @RequestBody VenueRateRequest request) {
        return ResponseEntity.ok(venueService.updateRate(venueId, rateId, request));
    }

    @DeleteMapping("/{venueId}/rates/{rateId}")
    @Operation(summary = "Supprimer un tarif")
    public ResponseEntity<Void> deleteRate(@PathVariable Long venueId, @PathVariable Long rateId) {
        venueService.deleteRate(venueId, rateId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{venueId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Photo de la salle (JPEG, PNG, GIF, WebP — max 2 Mo)")
    public ResponseEntity<Void> uploadImage(@PathVariable Long venueId, @RequestParam("file") MultipartFile file) throws IOException {
        venueService.setImage(venueId, file.getBytes());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{venueId}/image")
    @Operation(summary = "Photo de la salle (publique)")
    public ResponseEntity<byte[]> getImage(@PathVariable Long venueId) {
        byte[] image = venueService.getPublicImage(venueId);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectMediaType(image)))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(image);
    }

    @DeleteMapping("/{venueId}/image")
    @Operation(summary = "Supprimer la photo de la salle")
    public ResponseEntity<Void> deleteImage(@PathVariable Long venueId) {
        venueService.deleteImage(venueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{venueId}/calendar")
    @Operation(summary = "Réservations d'une salle pour un mois (month=AAAA-MM, défaut : mois courant)")
    public ResponseEntity<List<VenueBookingResponse>> calendar(@PathVariable Long venueId,
                                                               @RequestParam(required = false) String month) {
        YearMonth m;
        try {
            m = month == null || month.isBlank() ? null : YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Mois invalide (format attendu : AAAA-MM)");
        }
        return ResponseEntity.ok(bookingService.calendar(venueId, m));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Tableau de bord : encaissements par devise, occupation, impayés, à venir")
    public ResponseEntity<VenueDashboardResponse> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long venueId) {
        return ResponseEntity.ok(reportService.dashboard(from, to, venueId));
    }

    @GetMapping("/export.xlsx")
    @Operation(summary = "Export Excel des réservations et paiements d'une période")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] file = reportService.exportXlsx(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"salles-reservations.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    private String detectMediaType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return "image/gif";
        return "image/webp";
    }
}
