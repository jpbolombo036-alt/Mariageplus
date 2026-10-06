package com.mariageplus.controller;

import com.mariageplus.dto.checkin.OfflinePackResponse;
import com.mariageplus.dto.checkin.OfflineScanItem;
import com.mariageplus.dto.checkin.SyncCheckInRequest;
import com.mariageplus.dto.checkin.SyncCheckInResponse;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.service.AuthService;
import com.mariageplus.service.OrganizationSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckInOfflineIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;
    @Autowired private ObjectMapper objectMapper;

    private static String token;
    private static long eventId;
    private static long guestId;
    private static String publicToken;
    /** Invité RSVP ACCEPTED (3 pers.) dédié aux tests d'idempotence hors ligne. */
    private static String publicToken2;
    /** Invité SANS RSVP : refus métier attendu à la synchronisation. */
    private static String publicToken3;
    private static boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            var req = new com.mariageplus.dto.auth.RegisterRequest();
            req.setFirstName("Offline");
            req.setLastName("Checkin");
            req.setEmail("offline-checkin-" + java.util.UUID.randomUUID() + "@example.com");
            req.setPassword("password123");
            req.setOrganizationName("Org OfflineCheckin");
            var res = authService.register(req);
            organizationSettingsService.setEventCreationEnabled(res.getUser().getOrganizationId(), true);
            token = res.getAccessToken();

            String eventBody = mockMvc.perform(post("/api/events")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Mariage Offline\",\"type\":\"WEDDING\",\"weddingDetails\":{\"groomFirstName\":\"Jean\",\"groomLastName\":\"Kabongo\",\"brideFirstName\":\"Marie\",\"brideLastName\":\"Mukendi\"}}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            eventId = objectMapper.readTree(eventBody).get("id").asLong();

            String guestBody = mockMvc.perform(post("/api/events/" + eventId + "/guests")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"Alice\",\"lastName\":\"Invite\",\"phone\":\"225070102030\",\"email\":\"alice@test.com\",\"allowedCompanions\":1}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            guestId = objectMapper.readTree(guestBody).get("id").asLong();

            String invBody = mockMvc.perform(post("/api/events/" + eventId + "/invitations")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"guestId\":" + guestId + "}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long invitationId = objectMapper.readTree(invBody).get("id").asLong();

            String sendBody = mockMvc.perform(post("/api/events/" + eventId + "/invitations/" + invitationId + "/send")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            publicToken = objectMapper.readTree(sendBody).get("publicInviteUrl").asText()
                    .replaceAll(".*/", "");

            SubmitRsvpRequest rsvp = new SubmitRsvpRequest();
            rsvp.setStatus("ACCEPTED");
            rsvp.setNumberOfAttendees(2);
            mockMvc.perform(post("/api/public/invitations/" + publicToken + "/rsvp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(rsvp)))
                    .andExpect(status().isOk());

            // 2e invité confirmé (RSVP, 3 personnes) : tests d'idempotence hors ligne,
            // isolés de la capacité du 1er invité pour rendre les tests indépendants.
            long guest2Id = createGuest("Bob", "Idem", "bob-idem@test.com", "2250701020301");
            long invitation2Id = createInvitation(guest2Id);
            publicToken2 = sendInvitation(invitation2Id);
            submitRsvp(publicToken2, 3);

            // 3e invité : invitation envoyée SANS RSVP (refus métier à la sync).
            long guest3Id = createGuest("Carol", "NoRsvp", "carol-norsvp@test.com", "2250701020302");
            long invitation3Id = createInvitation(guest3Id);
            publicToken3 = sendInvitation(invitation3Id);

            initialized = true;
        }
    }

    private long createGuest(String firstName, String lastName, String email, String phone) throws Exception {
        String body = mockMvc.perform(post("/api/events/" + eventId + "/guests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName
                                + "\",\"email\":\"" + email + "\",\"phone\":\"" + phone
                                + "\",\"allowedCompanions\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createInvitation(long gid) throws Exception {
        String body = mockMvc.perform(post("/api/events/" + eventId + "/invitations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + gid + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String sendInvitation(long invitationId) throws Exception {
        String body = mockMvc.perform(post("/api/events/" + eventId + "/invitations/" + invitationId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("publicInviteUrl").asText().replaceAll(".*/", "");
    }

    private void submitRsvp(String tok, int attendees) throws Exception {
        SubmitRsvpRequest rsvp = new SubmitRsvpRequest();
        rsvp.setStatus("ACCEPTED");
        rsvp.setNumberOfAttendees(attendees);
        mockMvc.perform(post("/api/public/invitations/" + tok + "/rsvp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rsvp)))
                .andExpect(status().isOk());
    }

    private SyncCheckInRequest syncRequest(String qrToken, int attendees, String deviceId,
                                           Long sequence, LocalDateTime scannedAt) {
        SyncCheckInRequest request = new SyncCheckInRequest();
        request.setEventId(eventId);
        OfflineScanItem scan = new OfflineScanItem();
        scan.setQrToken(qrToken);
        scan.setNumberOfAttendees(attendees);
        scan.setScannedAt(scannedAt.toString());
        scan.setDeviceId(deviceId);
        scan.setSequence(sequence);
        request.setScans(List.of(scan));
        return request;
    }

    private SyncCheckInResponse postSync(SyncCheckInRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/checkins/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // UTF-8 explicite : les raisons métier sont accentuées.
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(response, SyncCheckInResponse.class);
    }

    @Test
    void offlinePack_returnsInvitationsAndTables() throws Exception {
        String response = mockMvc.perform(get("/api/checkins/event/{eventId}/offline-pack", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OfflinePackResponse pack = objectMapper.readValue(response, OfflinePackResponse.class);
        assertThat(pack.getEventId()).isEqualTo(eventId);
        // 3 invitations au total : Alice (ci-dessous) + les 2 fixtures hors ligne.
        assertThat(pack.getInvitations()).hasSize(3);
        var alice = pack.getInvitations().stream()
                .filter(i -> publicToken.equals(i.getPublicToken()))
                .findFirst().orElseThrow();
        assertThat(alice.getExpectedAttendees()).isEqualTo(2);
        assertThat(alice.isCanCheckIn()).isTrue();
    }

    @Test
    void syncOfflineScans_acceptsValidScan() throws Exception {
        SyncCheckInRequest request = new SyncCheckInRequest();
        request.setEventId(eventId);
        var scan = new com.mariageplus.dto.checkin.OfflineScanItem();
        scan.setQrToken(publicToken);
        scan.setNumberOfAttendees(1);
        scan.setScannedAt(LocalDateTime.now().minusMinutes(5).toString());
        scan.setDeviceId("DEVICE-1");
        scan.setSequence(1L);
        request.setScans(List.of(scan));

        String response = mockMvc.perform(post("/api/checkins/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SyncCheckInResponse sync = objectMapper.readValue(response, SyncCheckInResponse.class);
        assertThat(sync.getProcessed()).isEqualTo(1);
        assertThat(sync.getAccepted()).isEqualTo(1);
        assertThat(sync.getRejected()).isEqualTo(0);
        assertThat(sync.getResults()).hasSize(1);
        assertThat(sync.getResults().get(0).getStatus()).isEqualTo("ACCEPTED");
        assertThat(sync.getResults().get(0).getCheckInId()).isNotNull();
        assertThat(sync.getResults().get(0).getTotalAttendees()).isEqualTo(1);
        assertThat(sync.getResults().get(0).getRemainingAttendees()).isEqualTo(1);
    }

    @Test
    void syncOfflineScans_rejectsOverCapacity() throws Exception {
        SyncCheckInRequest request = new SyncCheckInRequest();
        request.setEventId(eventId);
        var scan = new com.mariageplus.dto.checkin.OfflineScanItem();
        scan.setQrToken(publicToken);
        scan.setNumberOfAttendees(5);
        scan.setScannedAt(LocalDateTime.now().minusMinutes(4).toString());
        scan.setDeviceId("DEVICE-1");
        scan.setSequence(2L);
        request.setScans(List.of(scan));

        String response = mockMvc.perform(post("/api/checkins/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SyncCheckInResponse sync = objectMapper.readValue(response, SyncCheckInResponse.class);
        assertThat(sync.getProcessed()).isEqualTo(1);
        assertThat(sync.getAccepted()).isEqualTo(0);
        assertThat(sync.getRejected()).isEqualTo(1);
        assertThat(sync.getResults().get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(sync.getResults().get(0).getReason()).contains("capacit");
    }

    @Test
    void syncOfflineScans_replayedBatch_isRecognizedAsDuplicate() throws Exception {
        // 1er envoi : le scan est accepté (invité 2 isolé, RSVP = 3).
        SyncCheckInResponse first = postSync(syncRequest(publicToken2, 1, "DEVICE-IDEMP", 9001L,
                LocalDateTime.now().minusMinutes(10)));
        assertThat(first.getAccepted()).isEqualTo(1);
        assertThat(first.getResults().get(0).getStatus()).isEqualTo("ACCEPTED");
        assertThat(first.getResults().get(0).getTotalAttendees()).isEqualTo(1);
        assertThat(first.getResults().get(0).getRemainingAttendees()).isEqualTo(2);

        // Rejeu du même lot avec un horodatage DIFFÉRENT (réponse réseau perdue) :
        // c'est la clé (device, sequence) qui fait foi, pas l'horodatage.
        SyncCheckInResponse replay = postSync(syncRequest(publicToken2, 1, "DEVICE-IDEMP", 9001L,
                LocalDateTime.now().minusMinutes(3)));
        assertThat(replay.getAccepted()).isEqualTo(0);
        assertThat(replay.getResults().get(0).getStatus()).isEqualTo("DUPLICATE");
        assertThat(replay.getResults().get(0).getReason()).contains("synchronisé");
        assertThat(replay.getResults().get(0).getTotalAttendees()).isEqualTo(1);
    }

    @Test
    void syncOfflineScans_rejectsFutureScannedAt() throws Exception {
        SyncCheckInResponse response = postSync(syncRequest(publicToken2, 1, "DEVICE-FUTUR", 1L,
                LocalDateTime.now().plusHours(2)));

        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(response.getResults().get(0).getReason()).contains("futur");
    }

    @Test
    void syncOfflineScans_rejectsMalformedScannedAt() throws Exception {
        SyncCheckInRequest request = new SyncCheckInRequest();
        request.setEventId(eventId);
        OfflineScanItem scan = new OfflineScanItem();
        scan.setQrToken(publicToken2);
        scan.setNumberOfAttendees(1);
        scan.setScannedAt("06/10/2026 14:30"); // format non ISO
        scan.setDeviceId("DEVICE-FORMAT");
        scan.setSequence(2L);
        request.setScans(List.of(scan));

        SyncCheckInResponse response = postSync(request);

        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(response.getResults().get(0).getReason()).contains("ISO");
    }

    @Test
    void syncOfflineScans_rejectsScanWithoutRsvp_withExplicitReason() throws Exception {
        SyncCheckInResponse response = postSync(syncRequest(publicToken3, 1, "DEVICE-NORSVP", 1L,
                LocalDateTime.now().minusMinutes(5)));

        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("REJECTED");
        // Raison métier propre (pas de préfixe "Erreur:" générique).
        assertThat(response.getResults().get(0).getReason()).contains("RSVP");
        assertThat(response.getResults().get(0).getReason()).doesNotContain("Erreur:");
    }
}
