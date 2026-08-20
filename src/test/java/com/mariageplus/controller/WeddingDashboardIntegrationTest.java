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
 * Étape 9 — Dashboard : statistiques issues des données réelles, dashboard vide
 * (taux = 0), et isolation multi-tenant (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeddingDashboardIntegrationTest {

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
            tokenA = register("dash-org@example.com", "Organisation Dashboard");
            weddingAId = createWedding(tokenA);
            initialized = true;
        }
    }

    private String register(String email, String orgName) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Org");
        req.setLastName("Dash");
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

    private Long createGuest() throws Exception {
        String name = "Guest" + (++counter);
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + name + "\",\"lastName\":\"Deep\",\"allowedCompanions\":4}"))
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

    private void submitRsvp(String qrToken, String status, int attendees) throws Exception {
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", qrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\",\"numberOfAttendees\":" + attendees + "}"))
                .andExpect(status().isOk());
    }

    private void checkIn(String qrToken, int attendees) throws Exception {
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + qrToken + "\",\"numberOfAttendees\":" + attendees + "}"))
                .andExpect(status().isCreated());
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
    void fullPipeline_dashboardReflectsReality() throws Exception {
        // 4 invités / 4 invitations
        Long g1 = createGuest();
        Long g2 = createGuest();
        Long g3 = createGuest();
        Long g4 = createGuest();

        Long inv1 = createInvitation(g1);
        Long inv2 = createInvitation(g2);
        Long inv3 = createInvitation(g3);
        createInvitation(g4);

        String t1 = publicToken(inv1);
        String t2 = publicToken(inv2);
        String t3 = publicToken(inv3);

        submitRsvp(t1, "ACCEPTED", 3);
        submitRsvp(t2, "ACCEPTED", 2);
        submitRsvp(t3, "DECLINED", 0);
        // g4 : aucune réponse

        checkIn(t1, 2);
        checkIn(t2, 1);

        Long tableA = createTable("Table A", 5);
        Long tableB = createTable("Table B", 8);
        assign(tableA, g1);
        assign(tableB, g2);

        mockMvc.perform(get("/api/weddings/{weddingId}/dashboard", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests.total").value(4))
                .andExpect(jsonPath("$.guests.unassigned").value(2))
                .andExpect(jsonPath("$.invitations.total").value(4))
                .andExpect(jsonPath("$.invitations.accepted").value(2))
                .andExpect(jsonPath("$.invitations.declined").value(1))
                .andExpect(jsonPath("$.invitations.pending").value(1))
                .andExpect(jsonPath("$.invitations.responseRate").value(75.0))
                .andExpect(jsonPath("$.attendance.expected").value(5))
                .andExpect(jsonPath("$.attendance.checkedIn").value(3))
                .andExpect(jsonPath("$.attendance.remaining").value(2))
                .andExpect(jsonPath("$.attendance.checkInRate").value(60.0))
                .andExpect(jsonPath("$.tables.total").value(2))
                .andExpect(jsonPath("$.tables.capacity").value(13))
                .andExpect(jsonPath("$.tables.assignedGuests").value(2))
                .andExpect(jsonPath("$.tables.remainingCapacity").value(11));
    }

    @Test
    void emptyWedding_allZeros() throws Exception {
        Long emptyWedding = createWedding(tokenA);
        mockMvc.perform(get("/api/weddings/{weddingId}/dashboard", emptyWedding)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests.total").value(0))
                .andExpect(jsonPath("$.guests.unassigned").value(0))
                .andExpect(jsonPath("$.invitations.total").value(0))
                .andExpect(jsonPath("$.invitations.responseRate").value(0.0))
                .andExpect(jsonPath("$.attendance.expected").value(0))
                .andExpect(jsonPath("$.attendance.checkInRate").value(0.0))
                .andExpect(jsonPath("$.tables.total").value(0))
                .andExpect(jsonPath("$.tables.capacity").value(0));
    }

    @Test
    void wrongOrganization_403() throws Exception {
        String otherToken = register("dash-other@example.com", "Autre Organisation");
        mockMvc.perform(get("/api/weddings/{weddingId}/dashboard", weddingAId)
                        .header("Authorization", auth(otherToken)))
                .andExpect(status().isForbidden());
    }
}