package com.mariageplus.controller;

import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.dto.rsvp.PublicRsvpResponse;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.service.InvitationCardService;
import com.mariageplus.service.RsvpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Accès public par publicToken (sans JWT). Ne retourne que des données minimales
 * et ne contourne jamais le soft-delete / l'annulation / l'expiration.
 */
@RestController
@RequestMapping("/api/public/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations publiques", description = "Accès public d'une invitation et soumission RSVP via son publicToken")
public class PublicInvitationController {

    private final RsvpService rsvpService;
    private final InvitationCardService invitationCardService;

    @GetMapping("/{publicToken}")
    @Operation(summary = "Données minimales d'une invitation (avec réponse RSVP courante)")
    public ResponseEntity<PublicInvitationResponse> getByToken(@PathVariable String publicToken) {
        return ResponseEntity.ok(rsvpService.getPublicInvitation(publicToken));
    }

    @PostMapping("/{publicToken}/rsvp")
    @Operation(summary = "Soumettre (ou mettre à jour) la réponse RSVP via le publicToken")
    public ResponseEntity<PublicRsvpResponse> submitRsvp(@PathVariable String publicToken,
                                                         @Valid @RequestBody SubmitRsvpRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(rsvpService.submitRsvp(publicToken, request));
    }

    @PostMapping("/{publicToken}/card")
    @Operation(summary = "Enregistrer la carte d'invitation confirmée (PNG généré par l'invité)")
    public ResponseEntity<Map<String, String>> uploadCard(@PathVariable String publicToken,
                                                          @RequestParam("file") MultipartFile file) {
        invitationCardService.uploadCard(publicToken, file);
        return ResponseEntity.ok(Map.of(
                "cardUrl", "/api/public/invitations/" + publicToken + "/card"));
    }

    @GetMapping("/{publicToken}/card")
    @Operation(summary = "Carte d'invitation confirmée (image PNG), 404 si absente")
    public ResponseEntity<byte[]> downloadCard(@PathVariable String publicToken) {
        InvitationCardService.CardImage card = invitationCardService.downloadCard(publicToken);
        if (card == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(card.contentType()))
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(card.bytes());
    }
}
