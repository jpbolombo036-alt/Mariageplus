package com.mariageplus.controller;

import com.mariageplus.dto.bulksend.BulkSendBatchResponse;
import com.mariageplus.dto.bulksend.BulkSendRequest;
import com.mariageplus.dto.bulksend.NotificationLogResponse;
import com.mariageplus.dto.PageResponse;
import com.mariageplus.service.AuthService;
import com.mariageplus.service.WhatsAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BulkSendControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private ObjectMapper objectMapper;

    @MockBean
    private WhatsAppService whatsAppService;

    private static String token;
    private static long weddingId;
    private static boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            var req = new com.mariageplus.dto.auth.RegisterRequest();
            req.setFirstName("Bulk");
            req.setLastName("Test");
            req.setEmail("bulk-test@example.com");
            req.setPassword("password123");
            req.setOrganizationName("Org BulkTest");
            var res = authService.register(req);
            token = res.getAccessToken();
            initialized = true;
        }

        when(whatsAppService.isConfigured()).thenReturn(true);
        when(whatsAppService.sendInvitationTemplate(any(), any(), any(), any(), any()))
                .thenReturn(true);

        if (weddingId == 0) {
            String body = mockMvc.perform(post("/api/events")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Mariage Bulk\",\"type\":\"WEDDING\",\"weddingDetails\":{\"groomFirstName\":\"Jean\",\"groomLastName\":\"Kabongo\",\"brideFirstName\":\"Marie\",\"brideLastName\":\"Mukendi\"}}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            weddingId = objectMapper.readTree(body).get("id").asLong();

            for (int i = 0; i < 3; i++) {
                String guestBody = mockMvc.perform(post("/api/events/" + weddingId + "/guests")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"firstName\":\"Guest" + i + "\",\"lastName\":\"Test\",\"phone\":\"225070102030" + i + "\",\"email\":\"guest" + i + "@test.com\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                long guestId = objectMapper.readTree(guestBody).get("id").asLong();

                mockMvc.perform(post("/api/events/" + weddingId + "/invitations")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"guestId\":" + guestId + "}"))
                        .andExpect(status().isCreated());
            }
        }
    }

    @Test
    void startBulkSend_returns202_withBatch() throws Exception {
        BulkSendRequest request = new BulkSendRequest();
        request.setChannel("WHATSAPP");

        MvcResult result = mockMvc.perform(post("/api/events/{weddingId}/invitations/send-bulk", weddingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.weddingId").value(weddingId))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andReturn();

        BulkSendBatchResponse batch = objectMapper.readValue(
                result.getResponse().getContentAsString(), BulkSendBatchResponse.class);
        assertThat(batch.getId()).isNotNull();
        assertThat(batch.getStatus()).isIn("PENDING", "IN_PROGRESS", "COMPLETED", "FAILED");
    }

    @Test
    void getBatch_returnsBatch() throws Exception {
        String batchBody = mockMvc.perform(post("/api/events/{weddingId}/invitations/send-bulk", weddingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkSendRequest() {{
                            setChannel("WHATSAPP");
                        }})))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        long batchId = objectMapper.readTree(batchBody).get("id").asLong();

        mockMvc.perform(get("/api/events/{weddingId}/invitations/send-bulk/{batchId}", weddingId, batchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batchId))
                .andExpect(jsonPath("$.weddingId").value(weddingId));
    }

    @Test
    void getLogs_returnsPaginatedLogs() throws Exception {
        String batchBody = mockMvc.perform(post("/api/events/{weddingId}/invitations/send-bulk", weddingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkSendRequest() {{
                            setChannel("WHATSAPP");
                        }})))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        long batchId = objectMapper.readTree(batchBody).get("id").asLong();

        mockMvc.perform(get("/api/events/{weddingId}/invitations/send-bulk/{batchId}/logs", weddingId, batchId)
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(10));
    }
}
