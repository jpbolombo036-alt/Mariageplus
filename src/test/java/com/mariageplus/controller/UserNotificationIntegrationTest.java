package com.mariageplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserNotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    @Test
    void superAdminWritesToOneOrganizationOrToAllOrganizers() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        LoginResponse alice = register("msg-alice@example.com", "Organisation Alice");
        LoginResponse bruno = register("msg-bruno@example.com", "Organisation Bruno");

        // Targeted: only Alice's organization receives it.
        JsonNode sent = send(admin, message("ORGANIZATION", alice.getUser().getOrganizationId(),
                "Bienvenue", "  Votre organisation est activée.  "), 200);
        assertEquals(1, sent.get("recipients").asInt());

        JsonNode aliceList = list(alice);
        assertEquals(1, aliceList.get("unreadCount").asLong());
        JsonNode welcome = aliceList.get("items").get(0);
        assertEquals("Bienvenue", welcome.get("title").asText());
        assertEquals("Votre organisation est activée.", welcome.get("message").asText());
        assertFalse(welcome.get("broadcast").asBoolean());
        assertEquals(0, list(bruno).get("unreadCount").asLong());

        // Broadcast: every active organizer, including both.
        JsonNode broadcast = send(admin, message("ALL_ORGANIZERS", null, "Maintenance", "Ce soir à 22h."), 200);
        assertTrue(broadcast.get("recipients").asInt() >= 2);
        assertNotNull(findByTitle(list(alice), "Maintenance"));
        JsonNode brunoMaintenance = findByTitle(list(bruno), "Maintenance");
        assertNotNull(brunoMaintenance);
        assertTrue(brunoMaintenance.get("broadcast").asBoolean());

        // An organizer cannot read or mark someone else's notification, nor send admin messages.
        mockMvc.perform(patch("/api/notifications/{id}/read", welcome.get("id").asLong())
                        .header("Authorization", bearer(bruno)))
                .andExpect(status().isNotFound());
        send(bruno, message("ALL_ORGANIZERS", null, "Pirate", "Non"), 403);

        // Targeted message without an organization never falls back to a broadcast.
        send(admin, message("ORGANIZATION", null, "Oubli", "Sans destinataire"), 400);
        send(admin, message("EVERYONE", null, "Inconnu", "Public invalide"), 400);
        send(admin, message("ORGANIZATION", 999_999L, "Absente", "Organisation inconnue"), 404);
        assertNull(findByTitle(list(bruno), "Oubli"));

        mockMvc.perform(patch("/api/notifications/{id}/read", welcome.get("id").asLong())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());
        assertEquals(1, list(alice).get("unreadCount").asLong());
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());
        assertEquals(0, list(alice).get("unreadCount").asLong());
        assertEquals(1, list(bruno).get("unreadCount").asLong(), "read-all only affects the caller");
    }

    private JsonNode send(LoginResponse who, Map<String, Object> body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/admin/messages").header("Authorization", bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    private static Map<String, Object> message(String audience, Long organizationId, String title, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("audience", audience);
        body.put("organizationId", organizationId);
        body.put("title", title);
        body.put("message", text);
        return body;
    }

    private JsonNode list(LoginResponse who) throws Exception {
        String body = mockMvc.perform(get("/api/notifications").header("Authorization", bearer(who)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static JsonNode findByTitle(JsonNode list, String title) {
        return StreamSupport.stream(list.get("items").spliterator(), false)
                .filter(n -> title.equals(n.get("title").asText()))
                .findFirst().orElse(null);
    }

    private LoginResponse register(String email, String organizationName) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Organisateur");
        request.setLastName("Test");
        request.setEmail(email);
        request.setPassword("MotDePasse123");
        request.setOrganizationName(organizationName);
        return authService.register(request);
    }

    private LoginResponse login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return authService.login(request);
    }

    private static String bearer(LoginResponse login) {
        return "Bearer " + login.getAccessToken();
    }
}
