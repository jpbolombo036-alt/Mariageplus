package com.mariageplus.controller;

import com.mariageplus.service.WhatsAppWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook Meta (Cloud API WhatsApp) — champ "messages".
 *
 * GET  : handshake de souscription (hub.verify_token ↔ WHATSAPP_WEBHOOK_VERIFY_TOKEN).
 * POST : notifications de statut (delivered / read / failed), signées
 *        X-Hub-Signature-256 avec le secret d'app (WHATSAPP_APP_SECRET).
 *
 * Point d'entrée public (SecurityConfig) : l'authentification repose sur le
 * handshake / la signature — Meta n'a pas de session utilisateur.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService webhookService;

    /** Handshake de souscription : Meta attend exactement le challenge en clair. */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        String accepted = webhookService.verifySubscription(mode, verifyToken, challenge);
        if (accepted == null) {
            log.warn("Webhook WhatsApp : handshake refusé (jeton de vérification invalide ou non configuré)");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(accepted);
    }

    /** Statuts de livraison — toujours 200 quand la signature est valide (sinon Meta retente). */
    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        if (!webhookService.isSignatureValid(signature, rawBody)) {
            log.warn("Webhook WhatsApp : signature X-Hub-Signature-256 invalide — notification rejetée");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        webhookService.processStatuses(rawBody);
        return ResponseEntity.ok().body(Map.of("received", true));
    }
}