package com.mariageplus.controller;

import com.mariageplus.dto.drink.DrinkResponse;
import com.mariageplus.service.DrinkService;
import com.mariageplus.service.RsvpService;
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
    private final DrinkService drinkService;

    @GetMapping("/{publicToken}/drinks")
    @Operation(summary = "Liste des boissons actives pour l'événement de l'invitation")
    public ResponseEntity<List<DrinkResponse>> listForInvitation(@PathVariable String publicToken) {
        Long eventId = rsvpService.resolveEventId(publicToken);
        return ResponseEntity.ok(drinkService.listActive(eventId));
    }
}
