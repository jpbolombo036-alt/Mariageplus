package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Étape 5 — Invitations : génération code/token, isolation, soft-delete, accès public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvitationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private InvitationRepository invitationRepository;

    private static String tokenA;
    private static String tokenB;
    private static String adminToken;
    private static Long weddingAId;
    private static Long weddingBId;
    private static Long guestAId;
    private static Long guestBId;
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

    private Long createGuest(String token, Long weddingId, String firstName) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"Guest\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestResponse.class).getId();
    }

    private Long createGuestWithEmail(String token, Long weddingId, String firstName, String email) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"Guest\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestResponse.class).getId();
    }

    private InvitationResponse createInvitation(String token, Long weddingId, Long guestId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingId)
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, InvitationResponse.class);
    }

    private String publicTokenOf(Long invitationId) {
        return invitationRepository.findById(invitationId).map(Invitation::getPublicToken).orElse(null);
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            LoginResponse a = authService.register(buildOrganizer("InvA", "inv-org-a@example.com"));
            LoginResponse b = authService.register(buildOrganizer("InvB", "inv-org-b@example.com"));
            tokenA = a.getAccessToken();
            tokenB = b.getAccessToken();
            LoginRequest adminLogin = new LoginRequest();
            adminLogin.setEmail("admin@test.mariageplus.app");
            adminLogin.setPassword("Admin@12345");
            adminToken = authService.login(adminLogin).getAccessToken();
            weddingAId = createWedding(tokenA);
            weddingBId = createWedding(tokenB);
            guestAId = createGuest(tokenA, weddingAId, "Jean");
            guestBId = createGuest(tokenB, weddingBId, "Pierre");
            initialized = true;
        }
    }
    @Test
    void organizer_createsInvitation_generatesCodeAndToken() throws Exception {
        Long charlie = createGuest(tokenA, weddingAId, "Charlie");
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + charlie + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationCode").exists())
                .andExpect(jsonPath("$.publicToken").doesNotExist());
    }

    @Test
    void organizer_cannotCreateInvitation_forGuestOfWeddingB() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestBId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizerA_cannotCreateInvitation_forWeddingB() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingBId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestBId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotListInvitations_ofWeddingB() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/invitations", weddingBId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotGetInvitation_ofWeddingB() throws Exception {
        Long g = createGuest(tokenB, weddingBId, "P1");
        InvitationResponse invitationB = createInvitation(tokenB, weddingBId, g);
        mockMvc.perform(get("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingBId, invitationB.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotUpdateInvitation_ofWeddingB() throws Exception {
        Long g = createGuest(tokenB, weddingBId, "P2");
        InvitationResponse invitation = createInvitation(tokenB, weddingBId, g);
        mockMvc.perform(put("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingBId, invitation.getId())
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotDeleteInvitation_ofWeddingB() throws Exception {
        Long g = createGuest(tokenB, weddingBId, "P3");
        InvitationResponse invitation = createInvitation(tokenB, weddingBId, g);
        mockMvc.perform(delete("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingBId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInvitation_duplicateForSameGuest_returns409() throws Exception {
        createInvitation(tokenA, weddingAId, guestAId);
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestAId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void public_getByToken_valid_returnsGuestInfo() throws Exception {
        Long alice = createGuest(tokenA, weddingAId, "Alice");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, alice);
        String token = publicTokenOf(invitation.getId());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestFirstName").value("Alice"));
    }

    @Test
    void public_getByToken_deleted_returns404() throws Exception {
        Long bob = createGuest(tokenA, weddingAId, "Bob");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, bob);
        String token = publicTokenOf(invitation.getId());
        mockMvc.perform(delete("/api/weddings/{weddingId}/invitations/{invitationId}",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void superAdmin_canListInvitations() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/invitations", weddingAId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void send_withoutGuestEmail_returns400() throws Exception {
        Long g = createGuest(tokenA, weddingAId, "NoMail");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, g);
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/send",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_withoutSmtp_marksSent_andReturnsShareUrl() throws Exception {
        Long g = createGuestWithEmail(tokenA, weddingAId, "Lea", "lea.send@example.com");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, g);
        String publicToken = publicTokenOf(invitation.getId());
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/send",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.emailSent").value(false))
                .andExpect(jsonPath("$.publicInviteUrl").value("http://localhost:3000/invitations/" + publicToken))
                .andExpect(jsonPath("$.sentAt").exists());
    }

    @Test
    void resend_beforeSend_returns409() throws Exception {
        Long g = createGuestWithEmail(tokenA, weddingAId, "Marc", "marc.resend@example.com");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, g);
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/resend",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isConflict());
    }

    @Test
    void send_thenResend_succeeds() throws Exception {
        Long g = createGuestWithEmail(tokenA, weddingAId, "Nina", "nina.resend@example.com");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, g);
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/send",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/resend",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.lastSentAt").exists());
    }

    @Test
    void cancel_thenPublicToken_returns404() throws Exception {
        Long g = createGuestWithEmail(tokenA, weddingAId, "Zoe", "zoe.cancel@example.com");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, g);
        String token = publicTokenOf(invitation.getId());
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/cancel",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/public/invitations/{publicToken}", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotateQr_returnsNewQr_andOldTokenBecomes404() throws Exception {
        Long g = createGuestWithEmail(tokenA, weddingAId, "Rosa", "rosa.rotate@example.com");
        InvitationResponse invitation = createInvitation(tokenA, weddingAId, g);
        String oldToken = publicTokenOf(invitation.getId());

        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/qr/rotate",
                        weddingAId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrDataUri").exists());

        // Le nouveau token (lu en base) diffère de l'ancien et résout l'invitation.
        String newToken = publicTokenOf(invitation.getId());
        assertNotEquals(oldToken, newToken);

        // L'ancien token est immédiatement invalide (fuite neutralisée).
        mockMvc.perform(get("/api/public/invitations/{publicToken}", oldToken))
                .andExpect(status().isNotFound());
        // Le nouveau token résout l'invitation.
        mockMvc.perform(get("/api/public/invitations/{publicToken}", newToken))
                .andExpect(status().isOk());
    }

    @Test
    void rotateQr_organizerA_cannotRotate_forWeddingB() throws Exception {
        Long g = createGuest(tokenB, weddingBId, "R1");
        InvitationResponse invitation = createInvitation(tokenB, weddingBId, g);
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/qr/rotate",
                        weddingBId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_cannotSendInvitation_ofWeddingB() throws Exception {
        Long g = createGuestWithEmail(tokenB, weddingBId, "Paul", "paul.b@example.com");
        InvitationResponse invitation = createInvitation(tokenB, weddingBId, g);
        mockMvc.perform(post("/api/weddings/{weddingId}/invitations/{invitationId}/send",
                        weddingBId, invitation.getId())
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isForbidden());
    }
}