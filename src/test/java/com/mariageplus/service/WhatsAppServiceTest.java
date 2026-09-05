package com.mariageplus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.Guest;
import com.mariageplus.exception.WhatsAppDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppServiceTest {

    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppService(RestClient.builder(), new ObjectMapper(),
                "https://graph.facebook.com");
        ReflectionTestUtils.setField(service, "token", "test-token");
        ReflectionTestUtils.setField(service, "phoneNumberId", "123456789");
        ReflectionTestUtils.setField(service, "templateName", "invitation_mariage");
        ReflectionTestUtils.setField(service, "templateLanguage", "fr");
        ReflectionTestUtils.setField(service, "apiVersion", "v23.0");
    }

    private Guest guest() {
        return Guest.builder().firstName("Tantine Claire").phone("+2250701020304").build();
    }

    private Event event() {
        return Event.builder().name("Josué & Eunice").message("Nous avons la joie de vous inviter").build();
    }

    /** Construit un service branché sur une fausse API renvoyant la réponse donnée. */
    private WhatsAppService wired(int status, String body) {
        return wired(status, body, null);
    }

    private WhatsAppService wired(int status, String body, AtomicReference<URI> capturedUri) {
        RestClient.Builder builder = RestClient.builder().requestFactory((uri, method) -> {
            if (capturedUri != null) {
                capturedUri.set(uri);
            }
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            request.setResponse(new MockClientHttpResponse(body.getBytes(), HttpStatus.valueOf(status)));
            return request;
        });
        WhatsAppService w = new WhatsAppService(builder, new ObjectMapper(),
                "https://graph.facebook.com");
        ReflectionTestUtils.setField(w, "token", "test-token");
        ReflectionTestUtils.setField(w, "phoneNumberId", "123456789");
        ReflectionTestUtils.setField(w, "templateName", "invitation_mariage");
        ReflectionTestUtils.setField(w, "templateLanguage", "fr");
        return w;
    }

    @Test
    void isConfigured_dependsOnCredentials() {
        assertFalse(new WhatsAppService(RestClient.builder(), new ObjectMapper(),
                "https://graph.facebook.com").isConfigured());
        assertTrue(service.isConfigured());
    }

    @Test
    void notConfigured_returnsFalseWithoutHttpCall() {
        assertFalse(new WhatsAppService(RestClient.builder(), new ObjectMapper(),
                "https://graph.facebook.com")
                .sendInvitationTemplate("2250701020304", guest(), event(),
                        "https://front/invitations/tok", null));
    }

    @Test
    void apiSuccess_returnsTrue() {
        assertTrue(wired(200, "{\"messages\":[{\"id\":\"wamid.123\"}]}")
                .sendInvitationTemplate("2250701020304", guest(), event(),
                        "https://front/invitations/tok123", null));
    }

    @Test
    void apiSuccess_usesAbsoluteGraphApiUri() {
        // Garde-fou : l'URI appelée doit être ABSOLUE (baseUrl configurée),
        // sinon l'appel échoue en production avec une exception générique.
        AtomicReference<URI> captured = new AtomicReference<>();
        boolean sent = wired(200, "{\"messages\":[{\"id\":\"wamid.123\"}]}", captured)
                .sendInvitationTemplate("2250701020304", guest(), event(),
                        "https://front/invitations/tok123", null);
        assertTrue(sent);
        assertTrue(captured.get().isAbsolute(),
                "L'URI de l'API Meta doit être absolue : " + captured.get());
        assertTrue(captured.get().getPath().endsWith("/123456789/messages"),
                "Le chemin doit contenir le phone number id : " + captured.get());
    }

    @Test
    void apiError_throwsWhatsAppDeliveryException() {
        assertThrows(WhatsAppDeliveryException.class, () -> wired(400,
                        "{\"error\":{\"message\":\"Recipient not in whatsapp\"}}")
                .sendInvitationTemplate("2250701020304", guest(), event(),
                        "https://front/invitations/tok123", null));
    }

    @Test
    void unexpectedResponse_throwsWhatsAppDeliveryException() {
        assertThrows(WhatsAppDeliveryException.class, () -> wired(200, "{\"unexpected\":true}")
                .sendInvitationTemplate("2250701020304", guest(), event(),
                        "https://front/invitations/tok123", null));
    }

    @Test
    void buildPersonalMessage_containsDateAndCustomMessage() {
        Event wedding = Event.builder()
                .name("Josué & Eunice")
                .message("Votre présence sera le plus beau des cadeaux")
                .eventDate(java.time.LocalDate.of(2026, 4, 11))
                .startTime(java.time.LocalTime.of(10, 0))
                .build();
        String message = assertDoesNotThrow(() -> service.buildPersonalMessage(wedding));
        assertTrue(message.contains("Votre présence sera le plus beau des cadeaux"));
        assertTrue(message.contains("11.04.2026"));
    }
}
