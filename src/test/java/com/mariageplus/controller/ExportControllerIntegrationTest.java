package com.mariageplus.controller;

import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExportControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    private static String tokenA;
    private static Long weddingAId;
    private static boolean initialized;

    private String auth(String token) {
        return "Bearer " + token;
    }

    private Long createWedding(String token) throws Exception {
        CreateWeddingRequest req = new CreateWeddingRequest();
        req.setGroomFirstName("Jean");
        req.setGroomLastName("Kabongo");
        req.setBrideFirstName("Marie");
        req.setBrideLastName("Mukendi");
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/weddings")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, WeddingResponse.class).getId();
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("Org");
            req.setLastName("ExportA");
            req.setEmail("export-org-a@example.com");
            req.setPassword("password123");
            req.setOrganizationName("Organisation ExportA");
            LoginResponse a = authService.register(req);
            tokenA = a.getAccessToken();
            weddingAId = createWedding(tokenA);
            initialized = true;
        }
    }

    @Test
    void exportGuestsCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/export/guests/csv", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"guests.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("id,firstName")));
    }

    @Test
    void exportInvitationsCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/export/invitations/csv", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"invitations.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("id,guestId")));
    }

    @Test
    void exportRsvpsCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/export/rsvps/csv", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"rsvps.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("id,invitationId")));
    }

    @Test
    void exportTablesCsv_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/export/tables/csv", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tables.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("tableId,tableName")));
    }

    @Test
    void exportReportPdf_returnsPdf() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/export/report/pdf", weddingAId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report.pdf\""))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(result -> {
                    byte[] bytes = result.getResponse().getContentAsByteArray();
                    assert bytes.length > 0;
                    assert bytes[0] == 0x25;
                });
    }

    @Test
    void exportWithoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/export/guests/csv", weddingAId))
                .andExpect(status().isUnauthorized());
    }
}
