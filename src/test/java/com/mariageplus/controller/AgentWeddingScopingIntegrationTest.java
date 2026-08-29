package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.dto.event.CreateEventRequest;
import com.mariageplus.dto.event.EventResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scoping des agents par mariage (end-to-end) : un GESTIONNAIRE_INVITES assigné au
 * mariage A accède à A (200) mais est refusé sur B (403), bien que les deux mariages
 * appartiennent à la même organisation. Vérifie la chaîne complète :
 * addMember (avec weddingId) → login agent → garde assertWeddingAccess.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AgentWeddingScopingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    private static String tokenOrganizer;
    private static Long orgId;
    private static Long weddingAId;
    private static Long weddingBId;
    private static String tokenAgent;
    private static boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            // 1. Organisateur (org unique) qui possède deux mariages.
            RegisterRequest org = new RegisterRequest();
            org.setFirstName("Org");
            org.setLastName("Scoping");
            org.setEmail(("scoping-org-" + java.util.UUID.randomUUID() + "@example.com"));
            org.setPassword("password123");
            org.setOrganizationName("Organisation Scoping");
            LoginResponse organizer = authService.register(org);
            tokenOrganizer = organizer.getAccessToken();
            orgId = organizer.getUser().getOrganizationId();

            weddingAId = createWedding("Wedding A");
            weddingBId = createWedding("Wedding B");

            // 2. Agent gestionnaire scopé au mariage A uniquement.
            OrganizationMemberRequest agent = new OrganizationMemberRequest();
            agent.setFirstName("Agent");
            agent.setLastName("Invites");
            String agentEmail = "agent-invites-" + java.util.UUID.randomUUID() + "@example.com";
            agent.setEmail(agentEmail);
            agent.setPassword("password123");
            agent.setRoleCode("GESTIONNAIRE_INVITES");
            agent.setWeddingId(weddingAId);

            String memberBody = mockMvc.perform(post("/api/organizations/{id}/members", orgId)
                            .header("Authorization", auth(tokenOrganizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(agent)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(memberBody).path("weddingId").asLong())
                    .describedAs("le membre agent doit être scopé au mariage A")
                    .isEqualTo(weddingAId);

            // 3. Login de l'agent → JWT rechargé avec ses mariages assignés.
            LoginRequest login = new LoginRequest();
            login.setEmail(agentEmail);
            login.setPassword("password123");
            tokenAgent = authService.login(login).getAccessToken();

            initialized = true;
        }
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private Long createWedding(String label) throws Exception {
        CreateEventRequest req = new CreateEventRequest();
        req.setName(label + " Groom & Bride");
        req.setType(com.mariageplus.entity.EventType.WEDDING);
        com.mariageplus.dto.event.WeddingDetailsRequest det = new com.mariageplus.dto.event.WeddingDetailsRequest();
        det.setGroomFirstName(label + "-Groom");
        det.setGroomLastName("Nom");
        det.setBrideFirstName(label + "-Bride");
        det.setBrideLastName("Nom");
        req.setWeddingDetails(det);
        String body = mockMvc.perform(post("/api/events")
                        .header("Authorization", auth(tokenOrganizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, EventResponse.class).getId();
    }

    @Test
    void agent_assignedWedding_canAccess() throws Exception {
        // GET /api/weddings/{wid}/guest-categories passe par loadInOrgScope (garde wedding).
        mockMvc.perform(get("/api/weddings/{weddingId}/guest-categories", weddingAId)
                        .header("Authorization", auth(tokenAgent)))
                .andExpect(status().isOk());
    }

    @Test
    void agent_weddingOutsideScope_isForbidden_evenInSameOrganization() throws Exception {
        mockMvc.perform(get("/api/weddings/{weddingId}/guest-categories", weddingBId)
                        .header("Authorization", auth(tokenAgent)))
                .andExpect(status().isForbidden());
    }

    @Test
    void agent_cannotAddAgentToOtherOrganization() throws Exception {
        // L'agent (non SUPER_ADMIN / ORGANISATEUR) ne peut pas gérer les membres.
        OrganizationMemberRequest agent = new OrganizationMemberRequest();
        agent.setFirstName("Agent");
        agent.setLastName("Deux");
        agent.setEmail("agent-deux@example.com");
        agent.setPassword("password123");
        agent.setRoleCode("AGENT_ACCUEIL");
        agent.setWeddingId(weddingAId);

        mockMvc.perform(post("/api/organizations/{id}/members", orgId)
                        .header("Authorization", auth(tokenAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agent)))
                .andExpect(status().isForbidden());
    }
}