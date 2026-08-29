package com.mariageplus.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Redirection de la page publique d'invitation vers l'application web
 * ({@code PublicInvitationView.vue} sur {@code /invitations/:token}).
 *
 * Historique : le backend servait auparavant sa propre page Thymeleaf
 * ({@code public-invitation.html}). La page web étant disponible et plus
 * riche, le backend se contente désormais de rediriger (302) — une seule
 * page d'invitation, celle du front. L'URL du front est déduite de
 * {@code app.frontend.url} (la même propriété que celle utilisée pour les
 * liens des emails). Les règles métier (404 si token inconnu / annulé /
 * expiré) restent appliquées côté web via l'API publique.
 */
@Controller
public class PublicInvitationPageController {

    @Value("${app.frontend.url:http://localhost:3000, https://mariaplus-web.vercel.app/}")
    private String frontendUrl;

    @GetMapping("/invitations/{publicToken}")
    public ResponseEntity<Void> invitation(@PathVariable String publicToken) {
        return redirectToFront(publicToken);
    }

    /**
     * Ancien endpoint de soumission RSVP de la page HTML (form POST).
     * Conservé pour compatibilité : redirige vers la page web, où le RSVP
     * se fait désormais via l'API publique
     * (POST /api/public/invitations/{token}/rsvp).
     */
    @PostMapping("/invitations/{publicToken}/rsvp")
    public ResponseEntity<Void> submit(@PathVariable String publicToken) {
        return redirectToFront(publicToken);
    }

    private ResponseEntity<Void> redirectToFront(String publicToken) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (base.isEmpty()) {
            // FRONTEND_URL non configurée : impossible de rediriger vers le front.
            // On NE redirige PAS en relatif (boucle 302 sur ce même endpoint) —
            // on renvoie une erreur explicite à corriger par la configuration.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Page d'invitation indisponible : FRONTEND_URL non configurée côté serveur.");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String target = UriComponentsBuilder.fromHttpUrl(base)
                .path("/invitations/")
                .path(publicToken)
                .build()
                .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(target))
                .build();
    }
}
