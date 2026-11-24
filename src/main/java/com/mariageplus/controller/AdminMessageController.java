package com.mariageplus.controller;

import com.mariageplus.dto.notification.AdminMessageRequest;
import com.mariageplus.security.CurrentUser;
import com.mariageplus.security.UserInfo;
import com.mariageplus.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Messages SUPER_ADMIN", description = "Message à une organisation ou à tous les organisateurs")
public class AdminMessageController {

    private final UserNotificationService service;

    @PostMapping
    @Operation(summary = "Envoyer un message dans la cloche des organisateurs")
    public ResponseEntity<Map<String, Integer>> send(@CurrentUser UserInfo currentUser,
                                                     @Valid @RequestBody AdminMessageRequest request) {
        int recipients = service.sendAdminMessage(currentUser.getId(), request);
        return ResponseEntity.ok(Map.of("recipients", recipients));
    }
}
