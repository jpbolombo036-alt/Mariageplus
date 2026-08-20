package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.dto.weddingevent.WeddingEventResponse;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Étape 3 — Événements de mariage : isolation par organisation + validations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeddingEventControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    private static String tokenA;
    private static String tokenB;
    private static String adminToken;
    private static Long weddingAId;
    private static Long weddingBId;
    private static boolean initialized;

    private RegisterRequest buildOrganizer(String suffix, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Org");
        req.setLastName(suffix);
        req.setEmail(email);
        req.setPassword("password123");
        req.setOrganizationName("Organisation " + suffix);
        return req;
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private Long createWedding(String token) throws Exception {
        CreateWeddingRequest req = new CreateWeddingRequest();
        req.setGroomFirstName("Jean");
        req.setGroomLastName("Kabongo");
        req.setBrideFirstName("Marie");
        req.setBrideLastName("Mukendi");
        String body = mockMvc.perform(post("/api/weddings")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, WeddingResponse.class).getId();
    }

    private Long createEvent(String token, Long weddingId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/events", weddingId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Réception\",\"type\":\"RECEPTION\","
                                + "\"eventDate\":\"2026-12-20\",\"startTime\":\"17:00\","
                                + "\"endTime\":\"23:00\",\"venueName\":\"Palace\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, WeddingEventResponse.class).getId();
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            LoginResponse a = authService.register(buildOrganizer("EvA", "event-org-a@example.com"));
            LoginResponse b = authService.register(buildOrganizer("EvB", "event-org-b@example.com"));
            tokenA = a.getAccessToken();
            tokenB = b.getAccessToken();
            LoginRequest adminLogin = new LoginRequest();
            adminLogin.setEmail("admin@test.mariageplus.app");
            adminLogin.setPassword("Admin@12345");
            adminToken = authService.login(adminLogin).getAccessToken();
            weddingAId = createWedding(tokenA);
            weddingBId = createWedding(tokenB);
            initialized = true;
        }
    }

    @Test
    void organizer_createsEvent_forOwnWedding() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/events", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Réception\",\"type\":\"RECEPTION\","
                                + "\"eventDate\":\"2026-12-20\",\"startTime\":\"17:00\","
                                + "\"endTime\":\"23:00\",\"venueName\":\"Palace\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weddingId").value(weddingAId))
                .andExpect(jsonPath("$.type").value("RECEPTION"));
    }

    @Test
    void organizerA_cannotCreateEvent_forWeddingB() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/events", weddingBId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"RECEPTION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotListEvents_ofWeddingB() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/events", weddingBId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }
    @Test
    void organizerA_cannotGetEvent_ofWeddingB() throws Exception {
        Long eventB = createEvent(tokenB, weddingBId);
        mockMvc.perform(get("/api/weddings/{weddingId}/events/{eventId}", weddingBId, eventB)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotUpdateEvent_ofWeddingB() throws Exception {
        Long eventB = createEvent(tokenB, weddingBId);
        mockMvc.perform(put("/api/weddings/{weddingId}/events/{eventId}", weddingBId, eventB)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotDeleteEvent_ofWeddingB() throws Exception {
        Long eventB = createEvent(tokenB, weddingBId);
        mockMvc.perform(delete("/api/weddings/{weddingId}/events/{eventId}", weddingBId, eventB)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEvent_endTimeBeforeStartTime_returns400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/events", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cérémonie\",\"type\":\"RELIGIOUS_CEREMONY\","
                                + "\"startTime\":\"14:00\",\"endTime\":\"13:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_invalidType_returns400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/events", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/events", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RECEPTION\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void superAdmin_canListEvents() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/events", weddingAId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }
}