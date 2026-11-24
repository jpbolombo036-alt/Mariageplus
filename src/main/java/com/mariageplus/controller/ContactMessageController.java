package com.mariageplus.controller;

import com.mariageplus.dto.notification.ContactMessageRequest;
import com.mariageplus.security.CurrentUser;
import com.mariageplus.security.UserInfo;
import com.mariageplus.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Message d'un utilisateur connecté vers l'équipe de la plateforme (ex. organisateur
 * en attente d'activation). Arrive dans la cloche des SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/contact-messages")
@RequiredArgsConstructor
@Tag(name = "Contact plateforme", description = "Message d'un utilisateur vers les SUPER_ADMIN")
public class ContactMessageController {

    private final AdminNotificationService service;

    @PostMapping
    @Operation(summary = "Envoyer un message à l'équipe de la plateforme (1 toutes les 10 minutes)")
    public ResponseEntity<Void> send(@CurrentUser UserInfo currentUser,
                                     @Valid @RequestBody ContactMessageRequest request) {
        service.sendContactMessage(currentUser.getId(), currentUser.getOrganizationId(), request.getMessage());
        return ResponseEntity.noContent().build();
    }
}
