package com.mariageplus.controller;

import com.mariageplus.dto.notification.AdminNotificationListResponse;
import com.mariageplus.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Notifications SUPER_ADMIN", description = "Cloche in-app : inscriptions et messages des organisateurs")
public class AdminNotificationController {

    private final AdminNotificationService service;

    @GetMapping
    @Operation(summary = "Dernières notifications et nombre de non lues")
    public ResponseEntity<AdminNotificationListResponse> list() {
        return ResponseEntity.ok(service.list());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
