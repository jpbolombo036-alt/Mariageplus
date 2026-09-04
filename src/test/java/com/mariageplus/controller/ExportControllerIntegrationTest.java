package com.mariageplus.controller;

import com.mariageplus.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExportControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private ObjectMapper objectMapper;

    private static String token;
    private static long weddingId;
    private static boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            var req = new com.mariageplus.dto.auth.RegisterRequest();
            req.setFirstName("Export");
            req.setLastName("Test");
            req.setEmail("export-test@example.com");
            req.setPassword("password123");
            req.setOrganizationName("Org ExportTest");
            var res = authService.register(req);
            token = res.getAccessToken();
            initialized = true;
        }

        if (weddingId == 0) {
            String body = mockMvc.perform(get("/api/events")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            if (objectMapper.readTree(body).isArray() && objectMapper.readTree(body).size() > 0) {
                weddingId = objectMapper.readTree(body).get(0).get("id").asLong();
            } else {
                String created = mockMvc.perform(post("/api/events")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Mariage Export\",\"type\":\"WEDDING\",\"weddingDetails\":{\"groomFirstName\":\"Jean\",\"groomLastName\":\"Kabongo\",\"brideFirstName\":\"Marie\",\"brideLastName\":\"Mukendi\"}}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                weddingId = objectMapper.readTree(created).get("id").asLong();
            }
        }
    }

    @Test
    void exportGuestsCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/events/{weddingId}/export/guests/csv", weddingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"guests.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("id,firstName,lastName")));
    }

    @Test
    void exportInvitationsCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/events/{weddingId}/export/invitations/csv", weddingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"invitations.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("id,guestId,invitationCode")));
    }

    @Test
    void exportRsvpsCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/events/{weddingId}/export/rsvps/csv", weddingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"rsvps.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("id,invitationId,guestId")));
    }

    @Test
    void exportTablesCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/events/{weddingId}/export/tables/csv", weddingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tables.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("tableId,tableName")));
    }
}
