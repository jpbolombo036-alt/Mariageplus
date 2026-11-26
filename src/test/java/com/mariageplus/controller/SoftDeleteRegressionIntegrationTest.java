package com.mariageplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.service.AuthService;
import com.mariageplus.service.OrganizationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suppression logique : le @SQLRestriction de BaseEntity (@MappedSuperclass)
 * n'était pas hérité, donc les éléments « supprimés » restaient visibles et
 * actifs. Chaque test vérifie qu'un élément supprimé disparaît vraiment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SoftDeleteRegressionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;

    private String token;
    private Long orgId;

    @BeforeEach
    void setUp() {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Org");
        r.setLastName("SoftDelete");
        r.setEmail("soft-delete-" + UUID.randomUUID() + "@example.com");
        r.setPassword("password123");
        r.setOrganizationName("Organisation SoftDelete");
        LoginResponse organizer = authService.register(r);
        token = organizer.getAccessToken();
        orgId = organizer.getUser().getOrganizationId();
        organizationSettingsService.setEventCreationEnabled(orgId, true);
    }

    @Test
    void removedMember_losesAccess_andCanBeAddedAgain() throws Exception {
        long eventId = createEvent("Mariage Membre");
        String email = "agent-" + UUID.randomUUID() + "@example.com";
        long memberId = addAgent(email, eventId);
        String agentToken = login(email);
        perform(get("/api/events/{id}/guest-categories", eventId), agentToken, 200);

        // Retiré de l'équipe : il ne doit plus rien voir, même avec un nouveau jeton.
        perform(delete("/api/organizations/{id}/members/{memberId}", orgId, memberId), token, 204);
        assertThat(json(perform(get("/api/organizations/{id}/members", orgId), token, 200)).toString()).doesNotContain(email);
        String afterRemoval = login(email);
        perform(get("/api/events/{id}/guest-categories", eventId), afterRemoval, 403);

        // Ré-ajouté au même événement avec le même rôle : accepté (pas d'erreur de doublon).
        addAgent(email, eventId);
        perform(get("/api/events/{id}/guest-categories", eventId), login(email), 200);
    }

    @Test
    void deletedEvent_isGone() throws Exception {
        long eventId = createEvent("Mariage Supprimé");
        perform(delete("/api/events/{id}", eventId), token, 204);
        perform(get("/api/events/{id}", eventId), token, 404);
        assertThat(json(perform(get("/api/events?size=200"), token, 200)).toString()).doesNotContain("Mariage Supprimé");
    }

    @Test
    void deletedCategoryAndDrink_areNoLongerListed() throws Exception {
        long eventId = createEvent("Mariage Catégories");
        long categoryId = json(perform(post("/api/events/{id}/guest-categories", eventId)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Famille à retirer"))), token, 201)).path("id").asLong();
        perform(delete("/api/events/{id}/guest-categories/{cid}", eventId, categoryId), token, 204);
        assertThat(json(perform(get("/api/events/{id}/guest-categories", eventId), token, 200)).toString())
                .doesNotContain("Famille à retirer");

        long drinkId = json(perform(post("/api/events/{id}/drinks", eventId)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Boisson à retirer"))), token, 201)).path("id").asLong();
        perform(delete("/api/events/{id}/drinks/{did}", eventId, drinkId), token, 204);
        perform(get("/api/events/{id}/drinks/{did}", eventId, drinkId), token, 404);
        assertThat(json(perform(get("/api/events/{id}/drinks", eventId), token, 200)).toString()).doesNotContain("Boisson à retirer");
    }

    // ------------------------------------------------------------------

    private long createEvent(String name) throws Exception {
        return json(perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", name, "type", "OTHER"))), token, 201)).path("id").asLong();
    }

    private long addAgent(String email, long eventId) throws Exception {
        return json(perform(post("/api/organizations/{id}/members", orgId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("firstName", "Agent", "lastName", "Retire", "email", email, "password", "password123",
                        "roleCode", "GESTIONNAIRE_INVITES", "weddingId", eventId))), token, 201)).path("id").asLong();
    }

    private String login(String email) {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        return authService.login(login).getAccessToken();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String bearer, int expectedStatus) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + bearer))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
