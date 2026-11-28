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
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminNotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    @Test
    void registrationAndContactMessageReachTheSuperAdminBell() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        LoginResponse organizer = register("bell-organizer@example.com", "Organisation Cloche");

        JsonNode registered = findByTitle(list(admin), "Nouvelle organisation à activer : Organisation Cloche");
        assertNotNull(registered, "registration must create a bell notification");
        assertEquals("ORGANIZATION_REGISTERED", registered.get("type").asText());
        assertEquals("bell-organizer@example.com", registered.get("senderEmail").asText());
        assertTrue(registered.get("readAt").isNull());

        // Organizers cannot read the SUPER_ADMIN bell.
        mockMvc.perform(get("/api/admin/notifications").header("Authorization", bearer(organizer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/contact-messages").header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "  Bonjour, pouvez-vous activer mon compte ?  "))))
                .andExpect(status().isNoContent());

        JsonNode message = findByTitle(list(admin), "Message de Compte Test — Organisation Cloche");
        assertNotNull(message, "contact message must reach the bell");
        assertEquals("CONTACT_MESSAGE", message.get("type").asText());
        assertEquals("Bonjour, pouvez-vous activer mon compte ?", message.get("message").asText());

        // One message per 10 minutes for the same user.
        mockMvc.perform(post("/api/contact-messages").header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "Encore moi"))))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/contact-messages").header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("message", "   "))))
                .andExpect(status().isBadRequest());

        long unreadBefore = list(admin).get("unreadCount").asLong();
        mockMvc.perform(patch("/api/admin/notifications/{id}/read", message.get("id").asLong())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        JsonNode afterOne = list(admin);
        assertEquals(unreadBefore - 1, afterOne.get("unreadCount").asLong());
        assertFalse(findByTitle(afterOne, "Message de Compte Test — Organisation Cloche").get("readAt").isNull());

        mockMvc.perform(post("/api/admin/notifications/read-all").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        assertEquals(0, list(admin).get("unreadCount").asLong());
    }

    private JsonNode list(LoginResponse admin) throws Exception {
        String body = mockMvc.perform(get("/api/admin/notifications").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static JsonNode findByTitle(JsonNode list, String title) {
        return StreamSupport.stream(list.get("items").spliterator(), false)
                .filter(n -> title.equals(n.get("title").asText()))
                .findFirst().orElse(null);
    }

    @Test
    void aVenueOwnerRegistrationAsksTheSuperAdminToEnableTheVenueModule() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Gerant");
        request.setLastName("Salle");
        request.setEmail("venue-owner@example.com");
        request.setPassword("MotDePasse123");
        request.setOrganizationName("Salle Cloche");
        request.setVenueOwner(true);
        authService.register(request);

        JsonNode registered = findByTitle(list(admin), "Nouvelle salle de fête à activer : Salle Cloche");
        assertNotNull(registered, "a venue owner registration must be flagged in the bell");
        assertTrue(registered.get("message").asText().contains("module Salles"));
    }

    private LoginResponse register(String email, String organizationName) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Compte");
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
