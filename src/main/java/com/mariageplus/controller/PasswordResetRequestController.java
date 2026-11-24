package com.mariageplus.controller;

import com.mariageplus.dto.auth.PasswordResetRequestBody;
import com.mariageplus.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * « Mot de passe oublié » sans SMTP : la demande arrive dans la cloche des
 * SUPER_ADMIN. Réponse identique que le compte existe ou non, pour ne pas révéler
 * les adresses inscrites. Limité par IP (AuthRateLimitFilter).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Authentification et inscription")
public class PasswordResetRequestController {

    static final String NEUTRAL_MESSAGE =
            "Si un compte correspond à cette adresse, notre équipe a été prévenue et vous contactera.";

    private final AdminNotificationService adminNotificationService;

    @PostMapping("/password-reset-requests")
    @Operation(summary = "Demander la réinitialisation de son mot de passe (traitée par un SUPER_ADMIN)")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody PasswordResetRequestBody body) {
        adminNotificationService.requestPasswordReset(body.getEmail());
        return ResponseEntity.accepted().body(Map.of("message", NEUTRAL_MESSAGE));
    }
}
