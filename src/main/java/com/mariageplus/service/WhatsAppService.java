package com.mariageplus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.Guest;
import com.mariageplus.exception.WhatsAppDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Envoi WhatsApp via la Cloud API Meta (template approuvé).
 *
 * Comportement calqué sur {@link InvitationMailService} :
 * - sans identifiants ({@code WHATSAPP_TOKEN} / {@code WHATSAPP_PHONE_NUMBER_ID}),
 *   aucun envoi n'est tenté et {@link #isConfigured()} renvoie false — le flux
 *   applicatif reste utilisable (lien à partager manuellement).
 * - une erreur de l'API lève {@link WhatsAppDeliveryException} (→ 502).
 *
 * Template attendu (variables positionnelles) :
 *   {{1}} = prénom invité, {{2}} = nom de l'événement (couple),
 *   {{3}} = message + date, bouton URL index 0 = {{1}} → publicToken.
 */
@Service
@Slf4j
public class WhatsAppService {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.FRENCH);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.whatsapp.token:}")
    private String token;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.template-name:invitation_mariage}")
    private String templateName;

    @Value("${app.whatsapp.template-language:fr}")
    private String templateLanguage;

    @Value("${app.whatsapp.api-version:v23.0}")
    private String apiVersion;

    /**
     * @param apiBaseUrl base de l'API Graph Meta (ex : https://graph.facebook.com) ;
     *                   obligatoire pour rendre l'URI de requête absolue en production.
     */
    public WhatsAppService(RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper,
                           @Value("${app.whatsapp.api-base-url:https://graph.facebook.com}") String apiBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    /** true si l'API WhatsApp est configurée (token + phone number id). */
    public boolean isConfigured() {
        return StringUtils.hasText(token) && StringUtils.hasText(phoneNumberId);
    }

    /**
     * Envoie le message template d'invitation au destinataire fourni.
     *
     * @param whatsAppId      identifiant WhatsApp du destinataire (chiffres uniquement)
     * @param guest           invité destinataire (prénom → variable {{1}})
     * @param event           événement (nom → {{2}}, message + date → {{3}})
     * @param publicInviteUrl lien public complet (le publicToken alimente le bouton URL)
     * @param imageUrl        URL publique de la photo de couverture (en-tête) ; null = sans image
     * @return true si l'API a accepté le message (identifiant de message reçu)
     * @throws WhatsAppDeliveryException si l'API rejette le message
     */
    public boolean sendInvitationTemplate(String whatsAppId,
                                          Guest guest,
                                          Event event,
                                          String publicInviteUrl,
                                          String imageUrl) {
        if (!isConfigured()) {
            log.warn("WhatsApp non configuré : aucun envoi tenté");
            return false;
        }

        Map<String, Object> payload = buildPayload(whatsAppId, guest, event, publicInviteUrl, imageUrl);
        try {
            String body = restClient.post()
                    .uri("/{apiVersion}/{phoneNumberId}/messages", apiVersion, phoneNumberId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body == null ? "{}" : body);
            boolean accepted = json.path("messages").isArray() && json.path("messages").size() > 0;
            if (!accepted) {
                throw new WhatsAppDeliveryException("Réponse inattendue de l'API WhatsApp");
            }
            return true;
        } catch (RestClientResponseException ex) {
            String detail = extractApiError(ex);
            log.error("Échec d'envoi WhatsApp vers {} : {}", whatsAppId, detail);
            throw new WhatsAppDeliveryException("Échec d'envoi WhatsApp : " + detail, ex);
        } catch (WhatsAppDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Erreur d'appel à l'API WhatsApp", ex);
            throw new WhatsAppDeliveryException("L'envoi WhatsApp a échoué. Réessayez plus tard.", ex);
        }
    }

    /** Corps texte du template : {{3}} = message personnalisé + date. */
    public String buildPersonalMessage(Event event) {
        StringBuilder sb = new StringBuilder();
        if (event.getMessage() != null && !event.getMessage().isBlank()) {
            sb.append(event.getMessage().trim());
        }
        if (event.getEventDate() != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("Rendez-vous le ").append(DATE_FR.format(event.getEventDate()));
            if (event.getStartTime() != null) {
                sb.append(" à ").append(event.getStartTime());
            }
            sb.append(".");
        }
        return sb.toString();
    }

    private Map<String, Object> buildPayload(String whatsAppId, Guest guest, Event event,
                                             String publicInviteUrl, String imageUrl) {
        List<Map<String, Object>> components = new ArrayList<>();
        if (StringUtils.hasText(imageUrl)) {
            components.add(Map.of(
                    "type", "header",
                    "parameters", List.of(Map.of("type", "image", "image", Map.of("link", imageUrl)))));
        }
        List<Map<String, Object>> bodyParams = new ArrayList<>();
        bodyParams.add(Map.of("type", "text", "text", safe(guest.getFirstName())));
        bodyParams.add(Map.of("type", "text", "text", safe(event.getName())));
        bodyParams.add(Map.of("type", "text", "text", safe(buildPersonalMessage(event))));
        components.add(Map.of("type", "body", "parameters", bodyParams));
        // Bouton URL (index 0) : variable = publicToken (dernier segment du lien public).
        String tokenPart = extractToken(publicInviteUrl);
        if (tokenPart != null) {
            components.add(Map.of(
                    "type", "button",
                    "sub_type", "url",
                    "index", "0",
                    "parameters", List.of(Map.of("type", "text", "text", tokenPart))));
        }

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", templateLanguage));
        template.put("components", components);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", whatsAppId);
        payload.put("type", "template");
        payload.put("template", template);
        return payload;
    }

    private String extractApiError(RestClientResponseException ex) {
        try {
            JsonNode json = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode error = json.path("error");
            String details = error.path("error_data").path("details").asText(null);
            if (details != null && !details.isBlank()) {
                return details;
            }
            String message = error.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // réponse non JSON : on retombe sur le statut HTTP
        }
        return "HTTP " + ex.getStatusCode().value();
    }

    private String extractToken(String publicInviteUrl) {
        if (publicInviteUrl == null || publicInviteUrl.isBlank()) {
            return null;
        }
        int idx = publicInviteUrl.lastIndexOf('/');
        return idx >= 0 && idx < publicInviteUrl.length() - 1
                ? publicInviteUrl.substring(idx + 1)
                : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
