package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
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
 * Étape 6 — RSVP public : accès par publicToken, idempotence, statuts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RsvpPublicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private InvitationRepository invitationRepository;
    @Autowired private RsvpRepository rsvpRepository;

    private static String tokenA;
    private static Long weddingAId;
    private static boolean initialized;
    private static int counter = 0;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("Org");
            req.setLastName("Rsvp");
            req.setEmail("rsvp-org@example.com");
            req.setPassword("password123");
            req.setOrganizationName("Organisation Rsvp");
            LoginResponse a = authService.register(req);
            tokenA = a.getAccessToken();
            weddingAId = createWedding(tokenA);
            initialized = true;
        }
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
        String name = "Invite" + (++counter);
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + name + "\",\"lastName\":\"Guest\",\"allowedCompanions\":2}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestResponse.class).getId();
    }

    private InvitationResponse createInvitation(Long guestId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, InvitationResponse.class);
    }

    private String tokenOf(Long invitationId) {
        return invitationRepository.findById(invitationId).map(Invitation::getPublicToken).orElse(null);
    }

    /** Crée une invitation valide et retourne son publicToken. */
    private String readyToken() throws Exception {
        return tokenOf(createInvitation(createGuest()).getId());
    }
    @Test
    void get_publicTokenValid_returnsGuestInfo() throws Exception {
        String token = readyToken();
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestFirstName").exists())
                .andExpect(jsonPath("$.guestLastName").exists())
                .andExpect(jsonPath("$.weddingDisplayName").exists())
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.rsvpStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rsvpNumberOfAttendees").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void get_unknownToken_returns404() throws Exception {
        mockMvc.perform(get("/api/public/invitations/unknown-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_deletedInvitation_returns404() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(delete("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingAId, inv.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_cancelledInvitation_returns404() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(put("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingAId, inv.getId())
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_rsvp_accepted_returns200() throws Exception {
        String token = readyToken();
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.numberOfAttendees").value(1));
    }

    @Test
    void post_rsvp_declined_returns200() throws Exception {
        String token = readyToken();
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECLINED\",\"numberOfAttendees\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("DECLINED"))
                .andExpect(jsonPath("$.numberOfAttendees").value(0));
    }
    @Test
    void post_rsvp_invalidValue_returns400() throws Exception {
        String token = readyToken();
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAYBE\",\"numberOfAttendees\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_rsvp_unknownToken_returns404() throws Exception {
        mockMvc.perform(post("/api/public/invitations/unknown-token/rsvp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_rsvp_cancelledInvitation_returns404() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(put("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingAId, inv.getId())
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rsvp_idempotent_sameResponse() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isOk());
        long count = rsvpRepository.findAll().stream().filter(r -> r.getInvitationId().equals(inv.getId())).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void get_expiredInvitation_returns404() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(put("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingAId, inv.getId())
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EXPIRED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_rsvp_expiredInvitation_returns404() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(put("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingAId, inv.getId())
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EXPIRED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rsvp_update_acceptedAfterDeclined() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECLINED\",\"numberOfAttendees\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.rsvpNumberOfAttendees").value(1));
        long count = rsvpRepository.findAll().stream().filter(r -> r.getInvitationId().equals(inv.getId())).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rsvp_update_declinedAfterAccepted() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":2}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECLINED\",\"numberOfAttendees\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("DECLINED"))
                .andExpect(jsonPath("$.rsvpNumberOfAttendees").value(0));
        long count = rsvpRepository.findAll().stream().filter(r -> r.getInvitationId().equals(inv.getId())).count();
        assertThat(count).isEqualTo(1);
    }
@Test
    void post_rsvp_accepted_attendeesBoundaries_returns200() throws Exception {
        String token = readyToken();
        // allowedCompanions = 2 → maximumAllowed = 3 : 2 et 3 acceptés
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfAttendees").value(2));
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfAttendees").value(3));
    }

    @Test
    void post_rsvp_accepted_attendeesZero_returns400() throws Exception {
        String token = readyToken();
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_rsvp_accepted_attendeesExceedsMax_returns400() throws Exception {
        String token = readyToken();
        // maximum = 3 → 4 refusé (le maximum est déterminé côté backend via le guest)
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_rsvp_declined_attendeesPositive_returns400() throws Exception {
        String token = readyToken();
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECLINED\",\"numberOfAttendees\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rsvp_update_chain_keepsSingleRow() throws Exception {
        InvitationResponse inv = createInvitation(createGuest());
        String token = tokenOf(inv.getId());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECLINED\",\"numberOfAttendees\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\",\"numberOfAttendees\":2}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.rsvpNumberOfAttendees").value(2));
        long count = rsvpRepository.findAll().stream().filter(r -> r.getInvitationId().equals(inv.getId())).count();
        assertThat(count).isEqualTo(1);
    }
}