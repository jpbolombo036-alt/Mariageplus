package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.venue.*;
import com.mariageplus.entity.VenueBookingStatus;
import com.mariageplus.service.VenueBookingService;
import com.mariageplus.service.VenueDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Réservations de salle : statuts, prestations, paiements, documents, lien événement. */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Salles — réservations", description = "Réservations, paiements USD/CDF, devis et reçus")
public class VenueBookingController {

    private final VenueBookingService bookingService;
    private final VenueDocumentService documentService;

    @GetMapping
    @Operation(summary = "Réservations (filtres : salle, client, statut, période)")
    public ResponseEntity<PageResponse<VenueBookingResponse>> list(
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) VenueBookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(bookingService.list(venueId, clientId, status, from, to, page, size));
    }

    @PostMapping
    @Operation(summary = "Créer une réservation (409 si le créneau est déjà pris)")
    public ResponseEntity<VenueBookingResponse> create(@Valid @RequestBody VenueBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Détail : prestations, paiements, reste à payer")
    public ResponseEntity<VenueBookingResponse> get(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.get(bookingId));
    }

    @PutMapping("/{bookingId}")
    @Operation(summary = "Modifier une réservation (champs null = inchangés ; 409 si le nouveau créneau est pris)")
    public ResponseEntity<VenueBookingResponse> update(@PathVariable Long bookingId,
                                                       @Valid @RequestBody VenueBookingRequest request) {
        return ResponseEntity.ok(bookingService.update(bookingId, request));
    }

    @PostMapping("/{bookingId}/confirm")
    @Operation(summary = "Confirmer (OPTION / DEMANDE → CONFIRMEE)")
    public ResponseEntity<VenueBookingResponse> confirm(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.confirm(bookingId));
    }

    @PostMapping("/{bookingId}/complete")
    @Operation(summary = "Marquer réalisée (CONFIRMEE → REALISEE)")
    public ResponseEntity<VenueBookingResponse> complete(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.complete(bookingId));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Annuler (libère la date ; annule l'événement lié)")
    public ResponseEntity<VenueBookingResponse> cancel(@PathVariable Long bookingId,
                                                       @Valid @RequestBody(required = false) CancelVenueBookingRequest request) {
        return ResponseEntity.ok(bookingService.cancel(bookingId, request == null ? null : request.getReason()));
    }

    @PostMapping("/{bookingId}/extras")
    @Operation(summary = "Ajouter une prestation (dans la devise de la réservation)")
    public ResponseEntity<VenueBookingResponse> addExtra(@PathVariable Long bookingId,
                                                         @Valid @RequestBody VenueBookingExtraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.addExtra(bookingId, request));
    }

    @DeleteMapping("/{bookingId}/extras/{extraId}")
    @Operation(summary = "Retirer une prestation")
    public ResponseEntity<VenueBookingResponse> removeExtra(@PathVariable Long bookingId, @PathVariable Long extraId) {
        return ResponseEntity.ok(bookingService.removeExtra(bookingId, extraId));
    }

    @GetMapping("/{bookingId}/payments")
    @Operation(summary = "Paiements de la réservation")
    public ResponseEntity<List<VenuePaymentResponse>> payments(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.listPayments(bookingId));
    }

    @PostMapping("/{bookingId}/payments")
    @Operation(summary = "Enregistrer un paiement (autre devise : exchangeRate = CDF pour 1 USD)")
    public ResponseEntity<VenuePaymentResponse> recordPayment(@PathVariable Long bookingId,
                                                              @Valid @RequestBody VenuePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.recordPayment(bookingId, request));
    }

    @DeleteMapping("/{bookingId}/payments/{paymentId}")
    @Operation(summary = "Annuler un paiement saisi par erreur")
    public ResponseEntity<Void> deletePayment(@PathVariable Long bookingId, @PathVariable Long paymentId) {
        bookingService.deletePayment(bookingId, paymentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}/payments/{paymentId}/receipt.pdf")
    @Operation(summary = "Reçu de paiement (PDF)")
    public ResponseEntity<byte[]> receipt(@PathVariable Long bookingId, @PathVariable Long paymentId) {
        return pdf(documentService.receiptPdf(bookingId, paymentId), "recu-" + paymentId + ".pdf");
    }

    @GetMapping("/{bookingId}/quote.pdf")
    @Operation(summary = "Devis (PDF)")
    public ResponseEntity<byte[]> quote(@PathVariable Long bookingId) {
        return pdf(documentService.quotePdf(bookingId), "devis-" + bookingId + ".pdf");
    }

    @PostMapping("/{bookingId}/event")
    @Operation(summary = "Créer l'événement MariagePlus pré-rempli (réservation CONFIRMEE)")
    public ResponseEntity<VenueBookingResponse> createEvent(@PathVariable Long bookingId,
                                                            @Valid @RequestBody(required = false) CreateEventFromBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createEvent(bookingId, request));
    }

    @PostMapping("/{bookingId}/client-access")
    @Operation(summary = "Donner au client l'accès à ses invités (GESTIONNAIRE_INVITES limité à son événement)")
    public ResponseEntity<VenueClientAccessResponse> clientAccess(@PathVariable Long bookingId,
                                                                  @Valid @RequestBody(required = false) VenueClientAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.grantClientAccess(bookingId, request));
    }

    private ResponseEntity<byte[]> pdf(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }
}
