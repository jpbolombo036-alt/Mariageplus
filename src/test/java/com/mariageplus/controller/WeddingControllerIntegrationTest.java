package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Étape 2 — Isolation des mariages par organisation + permissions + validations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeddingControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    private static Long orgAId;
    private static Long orgBId;
    private static String tokenA;
    private static String tokenB;
    private static String adminToken;
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

    @BeforeEach
    void setUp() {
        if (!initialized) {
            LoginResponse a = authService.register(buildOrganizer("WeddingA", "wedding-org-a@example.com"));
            LoginResponse b = authService.register(buildOrganizer("WeddingB", "wedding-org-b@example.com"));
            orgAId = a.getUser().getOrganizationId();
            orgBId = b.getUser().getOrganizationId();
            tokenA = a.getAccessToken();
            tokenB = b.getAccessToken();
            LoginRequest adminLogin = new LoginRequest();
            adminLogin.setEmail("admin@test.mariageplus.app");
            adminLogin.setPassword("Admin@12345");
            adminToken = authService.login(adminLogin).getAccessToken();
            initialized = true;
        }
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private CreateWeddingRequest buildCreate() {
        CreateWeddingRequest req = new CreateWeddingRequest();
        req.setGroomFirstName("Jean");
        req.setGroomLastName("Kabongo");
        req.setBrideFirstName("Marie");
        req.setBrideLastName("Mukendi");
        return req;
    }

    private Long createWedding(String token) throws Exception {
        String body = mockMvc.perform(post("/api/weddings")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreate())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, WeddingResponse.class).getId();
    }

    @Test
    void organizer_createsWedding_inOwnOrganization() throws Exception {
        String body = mockMvc.perform(post("/api/weddings")
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreate())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(orgAId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(body, WeddingResponse.class).getOrganizationId()).isEqualTo(orgAId);
    }

    @Test
    void organizer_cannotForceAnotherOrganization_onCreate() throws Exception {
        CreateWeddingRequest req = buildCreate();
        req.setOrganizationId(orgBId); // ignoré : le périmètre vient du principal
        mockMvc.perform(post("/api/weddings")
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(orgAId));
    }

    @Test
    void organizerA_cannotView_weddingOfB() throws Exception {
        Long weddingB = createWedding(tokenB);
        mockMvc.perform(get("/api/weddings/{id}", weddingB)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }
    @Test
    void organizerA_cannotUpdate_weddingOfB() throws Exception {
        Long weddingB = createWedding(tokenB);
        mockMvc.perform(put("/api/weddings/{id}", weddingB)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"hack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotArchive_weddingOfB() throws Exception {
        Long weddingB = createWedding(tokenB);
        mockMvc.perform(patch("/api/weddings/{id}/status", weddingB)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdmin_canView_weddingOfAnyOrganization() throws Exception {
        Long weddingB = createWedding(tokenB);
        mockMvc.perform(get("/api/weddings/{id}", weddingB)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void superAdmin_create_requiresOrganizationId() throws Exception {
        CreateWeddingRequest req = buildCreate();
        req.setOrganizationId(null);
        mockMvc.perform(post("/api/weddings")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingGroomFirstName_returns400() throws Exception {
        CreateWeddingRequest req = buildCreate();
        req.setGroomFirstName(null);
        mockMvc.perform(post("/api/weddings")
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_invalidStatus_returns400() throws Exception {
        Long wedding = createWedding(tokenA);
        mockMvc.perform(patch("/api/weddings/{id}/status", wedding)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_invalidTransition_returns400() throws Exception {
        Long wedding = createWedding(tokenA); // DRAFT → ARCHIVED interdit
        mockMvc.perform(patch("/api/weddings/{id}/status", wedding)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_validTransition_returnsOk() throws Exception {
        Long wedding = createWedding(tokenA); // DRAFT → PUBLISHED autorisé
        mockMvc.perform(patch("/api/weddings/{id}/status", wedding)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
}