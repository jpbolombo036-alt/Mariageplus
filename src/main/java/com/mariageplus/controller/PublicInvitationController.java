package com.mariageplus.controller;

import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.dto.rsvp.PublicRsvpResponse;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.service.RsvpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
