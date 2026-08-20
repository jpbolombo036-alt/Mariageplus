package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.guestcategory.GuestCategoryResponse;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Étape 4 — Catégories d'invités : isolation par mariage/organisation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GuestCategoryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    private static String tokenA;
    private static String tokenB;
    private static String adminToken;
    private static Long weddingAId;
    private static Long weddingBId;
    private static Long categoryBId;
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
        String body = mockMvc.perform(post("/api/weddings").header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, WeddingResponse.class).getId();
    }

    private Long createCategory(String token, Long weddingId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guest-categories", weddingId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VIP\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestCategoryResponse.class).getId();
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            LoginResponse a = authService.register(buildOrganizer("CatA", "cat-org-a@example.com"));
            LoginResponse b = authService.register(buildOrganizer("CatB", "cat-org-b@example.com"));
            tokenA = a.getAccessToken();
            tokenB = b.getAccessToken();
            LoginRequest adminLogin = new LoginRequest();
            adminLogin.setEmail("admin@test.mariageplus.app");
            adminLogin.setPassword("Admin@12345");
            adminToken = authService.login(adminLogin).getAccessToken();
            weddingAId = createWedding(tokenA);
            weddingBId = createWedding(tokenB);
            categoryBId = createCategory(tokenB, weddingBId);
            initialized = true;
        }
    }

    @Test
    void organizer_createsCategory_forOwnWedding() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/guest-categories", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Famille du marié\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weddingId").value(weddingAId));
    }

    @Test
    void organizerA_cannotCreateCategory_forWeddingB() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/guest-categories", weddingBId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }
    @Test
    void organizerA_cannotGet_categoryOfWeddingB() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/guest-categories/{categoryId}", weddingBId, categoryBId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotUpdate_categoryOfWeddingB() throws Exception {
        mockMvc.perform(put("/api/weddings/{weddingId}/guest-categories/{categoryId}", weddingBId, categoryBId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotDelete_categoryOfWeddingB() throws Exception {
        mockMvc.perform(delete("/api/weddings/{weddingId}/guest-categories/{categoryId}", weddingBId, categoryBId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/guest-categories", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void superAdmin_canListCategories() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/guest-categories", weddingAId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }
}
