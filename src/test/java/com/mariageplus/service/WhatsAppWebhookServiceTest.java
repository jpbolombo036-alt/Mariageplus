package com.mariageplus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.NotificationLog;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    @Mock
    private NotificationLogRepository logRepository;
    @Mock
    private InvitationRepository invitationRepository;

    private WhatsAppWebhookService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppWebhookService(logRepository, invitationRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "verifyToken", "mon-jeton-secret");
        ReflectionTestUtils.setField(service, "appSecret", "secret-app");
    }

    private NotificationLog log(String status) {
        return NotificationLog.builder().invitationId(5L).status(status).build();
    }

    private String payload(String wamid, String state) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\""
                + wamid + "\",\"status\":\"" + state + "\"}]}}]}]}";
    }

    private String hmacHex(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] d = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : d) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void verify_acceptsMatchingToken() {
        assertEquals("challenge-123",
                service.verifySubscription("subscribe", "mon-jeton-secret", "challenge-123"));
    }

    @Test
    void verify_rejectsWrongTokenOrUnconfigured() {
        assertNull(service.verifySubscription("subscribe", "autre-jeton", "challenge"));
        assertNull(service.verifySubscription("unsubscribe", "mon-jeton-secret", "challenge"));
        ReflectionTestUtils.setField(service, "verifyToken", "");
        assertNull(service.verifySubscription("subscribe", "mon-jeton-secret", "challenge"));
    }

    @Test
    void signature_acceptsValidHmac_rejectsOthers() throws Exception {
        String body = "{\"ok\":true}";
        assertTrue(service.isSignatureValid("sha256=" + hmacHex("secret-app", body), body));
        assertFalse(service.isSignatureValid("sha256=deadbeef", body));
        assertFalse(service.isSignatureValid(null, body));
        assertFalse(service.isSignatureValid("sha256=" + hmacHex("secret-app", body), "autre corps"));
    }

    @Test
    void signature_disabledWithoutSecret() {
        ReflectionTestUtils.setField(service, "appSecret", "");
        assertTrue(service.isSignatureValid(null, "{}"));
    }

    @Test
    void delivered_updatesLogAndInvitation() {
        NotificationLog nl = log("SENT");
        when(logRepository.findFirstByMessageIdOrderByIdDesc("wamid.1")).thenReturn(Optional.of(nl));
        Invitation invitation = Invitation.builder().build();
        when(invitationRepository.findById(5L)).thenReturn(Optional.of(invitation));

        service.processStatuses(payload("wamid.1", "delivered"));

        assertEquals("DELIVERED", nl.getStatus());
        assertNotNull(invitation.getDeliveredAt());
        verify(logRepository).save(nl);
        verify(invitationRepository).save(invitation);
    }

    @Test
    void read_updatesLog() {
        NotificationLog nl = log("DELIVERED");
        when(logRepository.findFirstByMessageIdOrderByIdDesc("wamid.2")).thenReturn(Optional.of(nl));
        Invitation invitation = Invitation.builder().deliveredAt(LocalDateTime.now()).build();
        when(invitationRepository.findById(5L)).thenReturn(Optional.of(invitation));

        service.processStatuses(payload("wamid.2", "read"));

        assertEquals("READ", nl.getStatus());
        verify(logRepository).save(nl);
    }

    @Test
    void failed_recordsMetaError() {
        NotificationLog nl = log("SENT");
        when(logRepository.findFirstByMessageIdOrderByIdDesc("wamid.3")).thenReturn(Optional.of(nl));

        String body = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.3\","
                + "\"status\":\"failed\",\"errors\":[{\"code\":131047,\"title\":\"Re-engagement\","
                + "\"message\":\"More than 24 hours have passed\"}]}]}}]}]}";
        service.processStatuses(body);

        assertEquals("FAILED", nl.getStatus());
        assertTrue(nl.getErrorMessage().contains("131047"));
        verify(logRepository).save(nl);
    }

    @Test
    void unknownMessageOrMalformedPayload_ignoredSilently() {
        when(logRepository.findFirstByMessageIdOrderByIdDesc("wamid.unknown"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.processStatuses(payload("wamid.unknown", "delivered")));
        assertDoesNotThrow(() -> service.processStatuses("{not-json"));
        assertDoesNotThrow(() -> service.processStatuses(null));

        verify(logRepository, never()).save(any(NotificationLog.class));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void delivered_neverDowngradesFailedLog() {
        NotificationLog nl = log("FAILED");
        when(logRepository.findFirstByMessageIdOrderByIdDesc("wamid.4")).thenReturn(Optional.of(nl));

        service.processStatuses(payload("wamid.4", "delivered"));

        assertEquals("FAILED", nl.getStatus());
        verify(logRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    void sentStatus_isIgnored() {
        NotificationLog nl = log("SENT");
        when(logRepository.findFirstByMessageIdOrderByIdDesc("wamid.5")).thenReturn(Optional.of(nl));

        service.processStatuses(payload("wamid.5", "sent"));

        assertEquals("SENT", nl.getStatus());
        verify(logRepository, never()).save(any(NotificationLog.class));
    }
}