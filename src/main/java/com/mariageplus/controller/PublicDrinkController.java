package com.mariageplus.controller;

import com.mariageplus.dto.drink.AvailableDrinkResponse;
import com.mariageplus.service.RsvpService;
import com.mariageplus.service.EventDrinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/invitations")
@RequiredArgsConstructor
@Tag(name = "Boissons publiques", description = "Accès public à la liste des boissons d'une invitation")
public class PublicDrinkController {

    private final RsvpService rsvpService;
    private final EventDrinkService eventDrinkService;

    @GetMapping("/{publicToken}/drinks")
    @Operation(summary = "Boissons DISPONIBLES pour l'événement de l'invitation (déclarées par l'organisateur)")
    public ResponseEntity<List<AvailableDrinkResponse>> listForInvitation(@PathVariable String publicToken) {
        Long eventId = rsvpService.resolveEventId(publicToken);
        return ResponseEntity.ok(eventDrinkService.listForInvitation(eventId));
    }
}

