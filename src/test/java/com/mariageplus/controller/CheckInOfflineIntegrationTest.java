package com.mariageplus.controller;

import com.mariageplus.dto.checkin.OfflinePackResponse;
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

            initialized = true;
        }
    }

    @Test
    void offlinePack_returnsInvitationsAndTables() throws Exception {
        String response = mockMvc.perform(get("/api/checkins/event/{eventId}/offline-pack", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OfflinePackResponse pack = objectMapper.readValue(response, OfflinePackResponse.class);
        assertThat(pack.getEventId()).isEqualTo(eventId);
        assertThat(pack.getInvitations()).hasSize(1);
        assertThat(pack.getInvitations().get(0).getPublicToken()).isEqualTo(publicToken);
        assertThat(pack.getInvitations().get(0).getExpectedAttendees()).isEqualTo(2);
        assertThat(pack.getInvitations().get(0).isCanCheckIn()).isTrue();
    }

    @Test
    void syncOfflineScans_acceptsValidScan() throws Exception {
        SyncCheckInRequest request = new SyncCheckInRequest();
        request.setEventId(eventId);
        var scan = new com.mariageplus.dto.checkin.OfflineScanItem();
        scan.setQrToken(publicToken);
        scan.setNumberOfAttendees(1);
        scan.setScannedAt("2026-10-06T14:30:00");
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
        scan.setScannedAt("2026-10-06T14:31:00");
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
}
