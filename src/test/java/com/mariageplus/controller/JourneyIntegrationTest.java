package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.entity.Invitation;
import com.mariageplus.repository.InvitationRepository;
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
 * Étape 10 — Parcours d'intégration complet : enregistrement → organisation →
 * mariage → catégorie → invité → invitation → public → RSVP → QR → scan →
 * check-in (+ refus dépassement) → table → affectation → dashboard, puis
 * isolation croisée (org B bloquée sur les ressources de org A).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JourneyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private InvitationRepository invitationRepository;

    private static String tokenA;
    private static Long weddingAId;
    private static boolean initialized;
    private static int counter = 0;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            tokenA = register("journey-org@example.com", "Organisation Journey");
            weddingAId = createWedding(tokenA);
            initialized = true;
        }
    }

    private String register(String email, String orgName) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Org");
        req.setLastName("Journey");
        req.setEmail(email);
        req.setPassword("password123");
        req.setOrganizationName(orgName);
        return authService.register(req).getAccessToken();
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

    private Long createCategory() throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guest-categories", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Famille\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asLong();
    }

    private Long createGuest(Long categoryId, int companions) throws Exception {
        String name = "Guest" + (++counter);
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + name + "\",\"lastName\":\"Zoe\",\"categoryId\":"
                                + categoryId + ",\"allowedCompanions\":" + companions + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestResponse.class).getId();
    }

    private Long createInvitation(Long guestId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, InvitationResponse.class).getId();
    }

    private String publicToken(Long invitationId) {
        return invitationRepository.findById(invitationId).orElseThrow().getPublicToken();
    }

    private Long createTable(String name, int capacity) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"capacity\":" + capacity + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asLong();
    }

    private void assign(Long tableId, Long guestId) throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void fullJourney_endToEnd() throws Exception {
        Long categoryId = createCategory();
        Long guestId = createGuest(categoryId, 4);
        Long invitationId = createInvitation(guestId);
        String token = publicToken(invitationId);

        // 1. Consultation publique (sans JWT)
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestFirstName").isNotEmpty());

        // 2. RSVP ACCEPTED (2 personnes)
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":2}"))
                .andExpect(status().isOk());

        // 3. QR généré
        mockMvc.perform(get("/api/weddings/{weddingId}/invitations/{invitationId}/qr", weddingAId, invitationId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrDataUri").isNotEmpty());

        // 4. Scan
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAttendees").value(2))
                .andExpect(jsonPath("$.canCheckIn").value(true));

        // 5. Check-in partiel (1)
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + token + "\",\"numberOfAttendees\":1}"))
                .andExpect(status().isCreated());

        // 6. Dépassement refusé (1 + 2 > attendu 2)
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + token + "\",\"numberOfAttendees\":2}"))
                .andExpect(status().isConflict());

        // 7. Table + affectation
        Long tableId = createTable("Table 1", 5);
        assign(tableId, guestId);

        // 8. Dashboard cohérent
        mockMvc.perform(get("/api/weddings/{weddingId}/dashboard", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests.total").value(1))
                .andExpect(jsonPath("$.invitations.total").value(1))
                .andExpect(jsonPath("$.invitations.accepted").value(1))
                .andExpect(jsonPath("$.attendance.expected").value(2))
                .andExpect(jsonPath("$.attendance.checkedIn").value(1))
                .andExpect(jsonPath("$.attendance.remaining").value(1))
                .andExpect(jsonPath("$.tables.total").value(1))
                .andExpect(jsonPath("$.tables.assignedGuests").value(1));
    }

    @Test
    void crossOrganization_idor_blocked() throws Exception {
        String tokenB = register("journey-other@example.com", "Autre Organisation");
        Long guestId = createGuest(null, 0);

        mockMvc.perform(get("/api/weddings/{weddingId}/guests/{guestId}", weddingAId, guestId)
                        .header("Authorization", auth(tokenB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/weddings/{weddingId}/dashboard", weddingAId)
                        .header("Authorization", auth(tokenB)))
                .andExpect(status().isForbidden());
    }
}