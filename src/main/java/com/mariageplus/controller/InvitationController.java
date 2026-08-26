package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.checkin.QrCodeResponse;
import com.mariageplus.dto.invitation.CreateInvitationRequest;
import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.dto.invitation.SendInvitationResponse;
import com.mariageplus.dto.invitation.UpdateInvitationRequest;
import com.mariageplus.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/weddings/{weddingId}/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Gestion administrative des invitations (isolées par organisation)")
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping
    @Operation(summary = "Liste paginée des invitations d'un mariage")
    public ResponseEntity<PageResponse<InvitationResponse>> list(
            @PathVariable Long weddingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(invitationService.list(weddingId, page, size, sortBy, sortDir));
    }

    @PostMapping
    @Operation(summary = "Créer une invitation (invitationCode et publicToken générés côté backend)")
    public ResponseEntity<InvitationResponse> create(@PathVariable Long weddingId,
                                                     @Valid @RequestBody CreateInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.create(weddingId, request));
    }

    @GetMapping("/{invitationId}")
    @Operation(summary = "Invitation par ID (périmètre vérifié)")
    public ResponseEntity<InvitationResponse> getById(@PathVariable Long weddingId,
                                                      @PathVariable Long invitationId) {
        return ResponseEntity.ok(invitationService.getById(weddingId, invitationId));
    }

    @PutMapping("/{invitationId}")
    @Operation(summary = "Modifier une invitation (périmètre vérifié)")
    public ResponseEntity<InvitationResponse> update(@PathVariable Long weddingId,
                                                     @PathVariable Long invitationId,
                                                     @Valid @RequestBody UpdateInvitationRequest request) {
        return ResponseEntity.ok(invitationService.update(weddingId, invitationId, request));
    }

    @DeleteMapping("/{invitationId}")
    @Operation(summary = "Supprimer une invitation (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long weddingId, @PathVariable Long invitationId) {
        invitationService.delete(weddingId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{invitationId}/qr")
    @Operation(summary = "QR code de l'invitation (data URI PNG, périmètre vérifié)")
    public ResponseEntity<QrCodeResponse> getQr(@PathVariable Long weddingId,
                                                @PathVariable Long invitationId) {
        return ResponseEntity.ok(invitationService.getQrData(weddingId, invitationId));
    }

    @PostMapping("/{invitationId}/qr/rotate")
    @Operation(summary = "Régénérer le QR (rotation du publicToken) : invalide l'ancien QR et renvoie le nouveau")
    public ResponseEntity<QrCodeResponse> rotateQr(@PathVariable Long weddingId,
                                                   @PathVariable Long invitationId) {
        return ResponseEntity.ok(invitationService.rotateQrToken(weddingId, invitationId));
    }

    @GetMapping("/pending-rsvp")
    @Operation(summary = "Invitations envoyées sans réponse (non-répondants, pour relance)")
    public ResponseEntity<List<InvitationResponse>> listNonResponders(@PathVariable Long weddingId) {
        return ResponseEntity.ok(invitationService.listNonResponders(weddingId));
    }

    @GetMapping("/pending-rsvp/count")
    @Operation(summary = "Nombre d'invitations envoyées sans réponse")
    public ResponseEntity<Long> countNonResponders(@PathVariable Long weddingId) {
        return ResponseEntity.ok(invitationService.countNonResponders(weddingId));
    }

    @PostMapping("/{invitationId}/send")
    @Operation(summary = "Envoyer l'invitation (email si SMTP configuré, sinon lien à partager)")
    public ResponseEntity<SendInvitationResponse> send(@PathVariable Long weddingId,
                                                       @PathVariable Long invitationId) {
        return ResponseEntity.ok(invitationService.send(weddingId, invitationId));
    }

    @PostMapping("/{invitationId}/resend")
    @Operation(summary = "Renvoyer l'invitation (uniquement si déjà envoyée)")
    public ResponseEntity<SendInvitationResponse> resend(@PathVariable Long weddingId,
                                                         @PathVariable Long invitationId) {
        return ResponseEntity.ok(invitationService.resend(weddingId, invitationId));
    }

    @PostMapping("/{invitationId}/cancel")
    @Operation(summary = "Annuler l'invitation (le lien public devient introuvable)")
    public ResponseEntity<InvitationResponse> cancel(@PathVariable Long weddingId,
                                                     @PathVariable Long invitationId) {
        return ResponseEntity.ok(invitationService.cancel(weddingId, invitationId));
    }
}
