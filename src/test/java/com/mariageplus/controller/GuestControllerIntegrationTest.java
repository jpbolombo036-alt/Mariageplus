package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.guest.GuestResponse;
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
 * Étape 4 — Invités : isolation par mariage/organisation + validation catégorie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GuestControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    private static String tokenA;
    private static String tokenB;
    private static String adminToken;
    private static Long weddingAId;
    private static Long weddingBId;
    private static Long categoryAId;
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

    private Long createGuest(String token, Long weddingId, Long categoryId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Kabongo\","
                                + "\"categoryId\":" + categoryId + ",\"allowedCompanions\":2}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestResponse.class).getId();
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            LoginResponse a = authService.register(buildOrganizer("GuestA", "guest-org-a@example.com"));
            LoginResponse b = authService.register(buildOrganizer("GuestB", "guest-org-b@example.com"));
            tokenA = a.getAccessToken();
            tokenB = b.getAccessToken();
            LoginRequest adminLogin = new LoginRequest();
            adminLogin.setEmail("admin@test.mariageplus.app");
            adminLogin.setPassword("Admin@12345");
            adminToken = authService.login(adminLogin).getAccessToken();
            weddingAId = createWedding(tokenA);
            weddingBId = createWedding(tokenB);
            categoryAId = createCategory(tokenA, weddingAId);
            categoryBId = createCategory(tokenB, weddingBId);
            initialized = true;
        }
    }
    @Test
    void organizer_createsGuest_withSameWeddingCategory() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Kabongo\","
                                + "\"categoryId\":" + categoryAId + ",\"allowedCompanions\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weddingId").value(weddingAId))
                .andExpect(jsonPath("$.categoryId").value(categoryAId));
    }

    @Test
    void organizerA_rejectsGuest_withOtherWeddingCategory() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Kabongo\","
                                + "\"categoryId\":" + categoryBId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizerA_cannotListGuests_ofWeddingB() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/guests", weddingBId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotGetGuest_ofWeddingB() throws Exception {
        Long guestB = createGuest(tokenB, weddingBId, categoryBId);
        mockMvc.perform(get("/api/weddings/{weddingId}/guests/{guestId}", weddingBId, guestB)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotUpdateGuest_ofWeddingB() throws Exception {
        Long guestB = createGuest(tokenB, weddingBId, categoryBId);
        mockMvc.perform(put("/api/weddings/{weddingId}/guests/{guestId}", weddingBId, guestB)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotDeleteGuest_ofWeddingB() throws Exception {
        Long guestB = createGuest(tokenB, weddingBId, categoryBId);
        mockMvc.perform(delete("/api/weddings/{weddingId}/guests/{guestId}", weddingBId, guestB)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createGuest_missingFirstName_returns400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"Kabongo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void superAdmin_canListGuests() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void importCsv_partialSuccess_reportsLineErrors() throws Exception {
        String csv = "firstName,lastName,email,allowedCompanions,categoryName\n"
                + "Alice,Ngoma,alice.import@example.com,1,VIP\n"
                + ",Bad,bad@example.com,0,\n"
                + "Bruno,Mbala,bruno.import@example.com,0,Inconnue\n";
        mockMvc.perform(multipart("/api/weddings/{weddingId}/guests/import", weddingAId)
                        .file("file", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(3));

        mockMvc.perform(get("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='alice.import@example.com')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.email=='bruno.import@example.com')]").isNotEmpty());
    }

    @Test
    void organizerA_cannotImportGuests_ofWeddingB() throws Exception {
        String csv = "firstName,lastName\nHack,Other\n";
        mockMvc.perform(multipart("/api/weddings/{weddingId}/guests/import", weddingBId)
                        .file("file", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }
}
