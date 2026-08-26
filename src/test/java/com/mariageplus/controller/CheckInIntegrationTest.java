package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.entity.Invitation;
import com.mariageplus.repository.CheckInRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Étape 7 — QR Code + Check-in : scan, entrées partielles, dépassement,
 * validation, permissions et isolation multi-tenant.
 *
 * <p>Note concurrence : H2 en mémoire ne permet pas de démontrer de façon fiable
 * un verrou multi-connexions. La stratégie (PESSIMISTIC_WRITE + recalcul de la
 * somme dans la transaction) est couverte par les tests unitaires
 * {@code checkIn_usesLockedQuery} / {@code checkIn_rejects_overrun_concurrentLike}.
 * Ici on vérifie l'invariant métier : le total entré ne dépasse jamais
 * RSVP.numberOfAttendees.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckInIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private InvitationRepository invitationRepository;
    @Autowired private CheckInRepository checkInRepository;

    private static String tokenA;
    private static Long weddingAId;
    private static boolean initialized;
    private static int counter = 0;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            tokenA = register("checkin-org@example.com", "Organisation Checkin");
            weddingAId = createWedding(tokenA);
            initialized = true;
        }
    }

    private String register(String email, String orgName) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Org");
        req.setLastName("Agent");
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
        return invitationRepository.findById(invitationId).orElseThrow().getPublicToken();
    }

    /** Crée une invitation avec (optionnellement) une réponse RSVP. */
    private String mintInvitation(String status, String attendees) throws Exception {
        Long guestId = createGuest();
        InvitationResponse inv = createInvitation(guestId);
        String qrToken = tokenOf(inv.getId());
        if (status != null) {
            mockMvc.perform(post("/api/public/invitations/{publicToken}/rsvp", qrToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + status + "\",\"numberOfAttendees\":" + attendees + "}"))
                    .andExpect(status().isOk());
        }
        return qrToken;
    }

    private void setInvitationStatus(Long invitationId, String status) throws Exception {
        mockMvc.perform(put("/api/weddings/{weddingId}/invitations/{invitationId}", weddingAId, invitationId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private String scanBody(String qr) {
        return scanBody(qr, weddingAId);
    }

    private String scanBody(String qr, Long weddingId) {
        return "{\"weddingId\":" + weddingId + ",\"qrToken\":\"" + qr + "\"}";
    }

    private String checkInBody(String qr, Integer attendees) {
        return checkInBody(qr, weddingAId, attendees);
    }

    private String checkInBody(String qr, Long weddingId, Integer attendees) {
        return "{\"weddingId\":" + weddingId + ",\"qrToken\":\"" + qr + "\",\"numberOfAttendees\":" + attendees + "}";
    }

    // ---------- Scan ----------

    @Test
    void scan_validQr_returnsState() throws Exception {
        String qr = mintInvitation("ACCEPTED", "3");
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(qr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestName").isNotEmpty())
                .andExpect(jsonPath("$.weddingDisplayName").isNotEmpty())
                .andExpect(jsonPath("$.expectedAttendees").value(3))
                .andExpect(jsonPath("$.checkedInAttendees").value(0))
                .andExpect(jsonPath("$.remainingAttendees").value(3))
                .andExpect(jsonPath("$.canCheckIn").value(true));
    }

    @Test
    void scan_unknownToken_404() throws Exception {
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody("unknown-token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void scan_cancelledInvitation_404() throws Exception {
        Long guestId = createGuest();
        InvitationResponse inv = createInvitation(guestId);
        setInvitationStatus(inv.getId(), "CANCELLED");
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(tokenOf(inv.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void scan_expiredInvitation_404() throws Exception {
        Long guestId = createGuest();
        InvitationResponse inv = createInvitation(guestId);
        setInvitationStatus(inv.getId(), "EXPIRED");
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(tokenOf(inv.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void scan_noRsvp_canCheckInFalse() throws Exception {
        String qr = mintInvitation(null, null);
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(qr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAttendees").value(0))
                .andExpect(jsonPath("$.canCheckIn").value(false));
    }

    @Test
    void scan_declined_canCheckInFalse() throws Exception {
        String qr = mintInvitation("DECLINED", "0");
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(qr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAttendees").value(0))
                .andExpect(jsonPath("$.canCheckIn").value(false));
    }

    @Test
    void scan_wrongOrganization_403() throws Exception {
        String otherOrgToken = register("checkin-other@example.com", "Autre Organisation");
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(otherOrgToken))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(qr)))
                .andExpect(status().isForbidden());
    }

    @Test
    void scan_wrongWedding_404() throws Exception {
        // Même organisation, mariage différent : on révèle rien (404 identique).
        Long weddingBId = createWedding(tokenA);
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(qr, weddingBId)))
                .andExpect(status().isNotFound());
    }

    // ---------- Check-in ----------

    @Test
    void checkIn_partialEntries_accumulateToExpected() throws Exception {
        String qr = mintInvitation("ACCEPTED", "3");
        Long invitationId = invitationRepository.findByPublicToken(qr).orElseThrow().getId();

        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkedInAttendees").value(1))
                .andExpect(jsonPath("$.remainingAttendees").value(2));

        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkedInAttendees").value(3))
                .andExpect(jsonPath("$.remainingAttendees").value(0));

        assertThat(checkInRepository.sumByInvitationId(invitationId)).isEqualTo(3);
    }

    @Test
    void checkIn_finalEntry_rejected_afterCapacityReached() throws Exception {
        String qr = mintInvitation("ACCEPTED", "3");
        Long invitationId = invitationRepository.findByPublicToken(qr).orElseThrow().getId();
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 2)))
                .andExpect(status().isCreated());
        assertThat(checkInRepository.sumByInvitationId(invitationId)).isEqualTo(3);
        // RSVP 3 = 1 + 2 déjà entrés, plus de place
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isConflict());
        assertThat(checkInRepository.sumByInvitationId(invitationId)).isEqualTo(3);
    }

    @Test
    void checkIn_overrun_rejected() throws Exception {
        String qr = mintInvitation("ACCEPTED", "3");
        Long invitationId = invitationRepository.findByPublicToken(qr).orElseThrow().getId();
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 2)))
                .andExpect(status().isCreated());
        // déjà 2, demande 2 → 2 + 2 > 3 → refusé, le total reste 2
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 2)))
                .andExpect(status().isConflict());
        assertThat(checkInRepository.sumByInvitationId(invitationId)).isEqualTo(2);
    }

    @Test
    void checkIn_noRsvp_409() throws Exception {
        String qr = mintInvitation(null, null);
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isConflict());
    }

    @Test
    void checkIn_declined_409() throws Exception {
        String qr = mintInvitation("DECLINED", "0");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isConflict());
    }

    @Test
    void checkIn_cancelled_404() throws Exception {
        Long guestId = createGuest();
        InvitationResponse inv = createInvitation(guestId);
        setInvitationStatus(inv.getId(), "CANCELLED");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(tokenOf(inv.getId()), 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkIn_zero_400() throws Exception {
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"" + qr + "\",\"numberOfAttendees\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkIn_negative_400() throws Exception {
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"" + qr + "\",\"numberOfAttendees\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkIn_missingNumber_400() throws Exception {
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"" + qr + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkIn_wrongOrganization_403() throws Exception {
        String otherOrgToken = register("checkin-org2@example.com", "Autre Org 2");
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(otherOrgToken))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkIn_wrongWedding_404() throws Exception {
        // Même organisation, mariage différent : on révèle rien (404 identique).
        Long weddingBId = createWedding(tokenA);
        String qr = mintInvitation("ACCEPTED", "2");
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, weddingBId, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_thenScan_remainingIsCreditedBack() throws Exception {
        String qr = mintInvitation("ACCEPTED", "3");
        String created = mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingAttendees").value(1))
                .andReturn().getResponse().getContentAsString();
        Long checkInId = objectMapper.readTree(created).get("checkInId").asLong();

        mockMvc.perform(delete("/api/checkins/{checkInId}", checkInId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/checkins/scan").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody(qr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInAttendees").value(0))
                .andExpect(jsonPath("$.remainingAttendees").value(3))
                .andExpect(jsonPath("$.canCheckIn").value(true));
    }

    @Test
    void cancel_thenCheckIn_allowedAgain() throws Exception {
        String qr = mintInvitation("ACCEPTED", "1");
        String created = mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long checkInId = objectMapper.readTree(created).get("checkInId").asLong();

        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/checkins/{checkInId}", checkInId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkedInAttendees").value(1));
    }

    @Test
    void cancel_unknown_404() throws Exception {
        mockMvc.perform(delete("/api/checkins/{checkInId}", 99999L)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_wrongOrganization_403() throws Exception {
        String qr = mintInvitation("ACCEPTED", "2");
        String created = mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(qr, 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long checkInId = objectMapper.readTree(created).get("checkInId").asLong();
        String otherOrgToken = register("checkin-cancel-other@example.com", "Org Cancel Other");
        mockMvc.perform(delete("/api/checkins/{checkInId}", checkInId)
                        .header("Authorization", auth(otherOrgToken)))
                .andExpect(status().isForbidden());
    }

    private void checkIn(Long invitationId) throws Exception {
        mockMvc.perform(post("/api/checkins").header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(tokenOf(invitationId), 1)))
                .andExpect(status().isCreated());
    }
}