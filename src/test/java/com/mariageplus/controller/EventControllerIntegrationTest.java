package com.mariageplus.controller;

import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.event.CreateEventRequest;
import com.mariageplus.dto.event.WeddingDetailsRequest;
import com.mariageplus.entity.EventDressCode;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration du module événements (racine unifiée, Phase 1/2).
 * Valide : création mariage (weddingDetails requis), règle D2 (rejet pour
 * les autres types), lecture avec weddingDetails + sessions, transitions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.mariageplus.repository.InvitationRepository invitationRepository;

    private static String token;
    private static boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("Org");
            req.setLastName("EventA");
            req.setEmail("events-root-org@example.com");
            req.setPassword("password123");
            req.setOrganizationName("Organisation EventA");
            LoginResponse res = authService.register(req);
            // Nouveau compte = verrouillé par défaut (WhatsApp + création OFF) :
            // on déverrouille l'organisation pour exercer le flux événementiel.
            organizationSettingsService.setEventCreationEnabled(res.getUser().getOrganizationId(), true);
            organizationSettingsService.setWhatsappEnabled(res.getUser().getOrganizationId(), true);
            token = res.getAccessToken();
            initialized = true;
        }
    }

    private CreateEventRequest weddingRequest() {
        CreateEventRequest req = new CreateEventRequest();
        req.setName("Mariage Test");
        req.setType(com.mariageplus.entity.EventType.WEDDING);
        WeddingDetailsRequest details = new WeddingDetailsRequest();
        details.setGroomFirstName("Jean");
        details.setGroomLastName("Kabongo");
        details.setBrideFirstName("Marie");
        details.setBrideLastName("Mukendi");
        req.setWeddingDetails(details);
        return req;
    }

    @Test
    void createWeddingEvent_returns201_withWeddingDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weddingRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WEDDING"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.weddingDetails.displayName").value("Jean Kabongo & Marie Mukendi"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("weddingDetails");
    }

    @Test
    void createWeddingEvent_withoutDetails_returns400() throws Exception {
        CreateEventRequest req = weddingRequest();
        req.setWeddingDetails(null);
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCollation_withWeddingDetails_returns400() throws Exception {
        CreateEventRequest req = weddingRequest();
        req.setType(com.mariageplus.entity.EventType.COLLATION);
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCollation_withoutDetails_returns201() throws Exception {
        CreateEventRequest req = new CreateEventRequest();
        req.setName("Collation Test");
        req.setType(com.mariageplus.entity.EventType.COLLATION);
        req.setDressCode(EventDressCode.COCKTAIL);
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weddingDetails").doesNotExist())
                .andExpect(jsonPath("$.type").value("COLLATION"))
                .andExpect(jsonPath("$.dressCode").value("COCKTAIL"));
    }

    @Test
    void getById_returnsEventWithSessions() throws Exception {
        String body = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weddingRequest())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long eventId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/events/{eventId}/sessions", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cérémonie civile\",\"type\":\"CIVIL_CEREMONY\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/events/{id}", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weddingDetails.groomFirstName").value("Jean"))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].type").value("CIVIL_CEREMONY"));
    }

    @Test
    void updateStatus_validTransition_succeeds_andInvalid_fails() throws Exception {
        String body = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weddingRequest())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long eventId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(patch("/api/events/{id}/status", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Transition invalide : PUBLISHED → COMPLETED
        mockMvc.perform(patch("/api/events/{id}/status", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Les options de tenue sont alimentées par l'enum backend (libellés FR) :
     * le front ne doit jamais coder les libellés en dur.
     */
    @Test
    void dressCodes_returnsOptionsWithFrenchLabels() throws Exception {
        mockMvc.perform(get("/api/events/dress-codes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(6)))
                .andExpect(jsonPath("$[0].value").value("CASUAL"))
                .andExpect(jsonPath("$[0].label").value("Tenue décontractée"))
                .andExpect(jsonPath("$[4].value").value("BLACK_TIE"))
                .andExpect(jsonPath("$[4].label").value("Cravate noire"))
                .andExpect(jsonPath("$[4].description").value("Smoking pour les hommes, robe longue pour les femmes"));
    }

    /**
     * Chaîne complète du vestiaire : l'organisateur renseigne la tenue, et
     * l'invité la reçoit sur la page publique (libellé + précision, sans JWT).
     */
    @Test
    void publicInvitation_exposesDressCodeWithFrenchLabel() throws Exception {
        CreateEventRequest req = weddingRequest();
        req.setDressCode(EventDressCode.BLACK_TIE);
        String evBody = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long eventId = objectMapper.readTree(evBody).get("id").asLong();

        String guestBody = mockMvc.perform(post("/api/events/" + eventId + "/guests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Invite\",\"lastName\":\"Tenue\",\"phone\":\"2250701020399\",\"email\":\"dresscode@test.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long guestId = objectMapper.readTree(guestBody).get("id").asLong();

        mockMvc.perform(post("/api/events/" + eventId + "/invitations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated());

        // publicToken n'est pas exposé dans la réponse administrative : on le lit en base.
        String publicToken = invitationRepository.findByWeddingId(eventId).get(0).getPublicToken();

        mockMvc.perform(get("/api/public/invitations/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dressCode").value("BLACK_TIE"))
                .andExpect(jsonPath("$.dressCodeLabel").value("Cravate noire"))
                .andExpect(jsonPath("$.dressCodeDescription")
                        .value("Smoking pour les hommes, robe longue pour les femmes"));
    }
}


