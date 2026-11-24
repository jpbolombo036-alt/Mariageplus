package com.mariageplus.controller;

import com.mariageplus.dto.notification.UserNotificationResponse;
import com.mariageplus.security.CurrentUser;
import com.mariageplus.security.UserInfo;
import com.mariageplus.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Cloche de l'utilisateur connecté : uniquement ses propres notifications. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications utilisateur", description = "Messages reçus de l'équipe de la plateforme")
public class UserNotificationController {

    private final UserNotificationService service;

    @GetMapping
    @Operation(summary = "Mes dernières notifications et le nombre de non lues")
    public ResponseEntity<UserNotificationResponse.ListResponse> list(@CurrentUser UserInfo currentUser) {
        return ResponseEntity.ok(service.list(currentUser.getId()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une de mes notifications comme lue")
    public ResponseEntity<Void> markRead(@CurrentUser UserInfo currentUser, @PathVariable Long id) {
        service.markRead(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Marquer toutes mes notifications comme lues")
    public ResponseEntity<Void> markAllRead(@CurrentUser UserInfo currentUser) {
        service.markAllRead(currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
