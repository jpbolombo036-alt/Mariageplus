package com.mariageplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.service.AuthService;
import com.mariageplus.service.OrganizationSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GuestControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void deleteGuest_removesItFromListsDetailsAndCounters() throws Exception {
        RegisterRequest registration = new RegisterRequest();
        registration.setFirstName("Test");
        registration.setLastName("Suppression");
        registration.setEmail("guest-delete-integration@example.com");
        registration.setPassword("password123");
        registration.setOrganizationName("Organisation suppression invite");
        LoginResponse login = authService.register(registration);
        organizationSettingsService.setEventCreationEnabled(login.getUser().getOrganizationId(), true);
        String authorization = "Bearer " + login.getAccessToken();

        String eventBody = mockMvc.perform(post("/api/events")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test suppression invite",
                                  "type": "OTHER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long eventId = objectMapper.readTree(eventBody).path("id").asLong();

        String guestBody = mockMvc.perform(post("/api/events/{eventId}/guests", eventId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Invite",
                                  "lastName": "Temporaire"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode guestJson = objectMapper.readTree(guestBody);
        long guestId = guestJson.path("id").asLong();

        mockMvc.perform(delete("/api/events/{eventId}/guests/{guestId}", eventId, guestId)
                        .header("Authorization", authorization))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{eventId}/guests", eventId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/events/{eventId}/guests/{guestId}", eventId, guestId)
                        .header("Authorization", authorization))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/events/{eventId}/dashboard", eventId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests.total").value(0));

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(0));
    }
}
