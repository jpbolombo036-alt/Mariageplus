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
import org.springframework.mock.web.MockMultipartFile;
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
        req.setDressColors(java.util.List.of(EventDressCode.BLACK));
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weddingDetails").doesNotExist())
                .andExpect(jsonPath("$.type").value("COLLATION"))
                .andExpect(jsonPath("$.dressColors[0]").value("BLACK"));
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
     * Le vestiaire porte sur la COULEUR, pas sur un style : les options sont des
     * teintes (noir, blanc, gris…) avec un code couleur pour l'aperçu. Les
     * libellés viennent de l'enum backend, le front ne doit rien coder en dur.
     */
    @Test
    void dressCodes_returnsColorOptionsWithFrenchLabels() throws Exception {
        mockMvc.perform(get("/api/events/dress-codes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(9)))
                .andExpect(jsonPath("$[0].value").value("BLACK"))
                .andExpect(jsonPath("$[0].label").value("Noir"))
                .andExpect(jsonPath("$[0].hex").value("#1F1F1F"))
                .andExpect(jsonPath("$[0].description").value("Tenue noire : du plus sobre au plus chic"))
                .andExpect(jsonPath("$[1].value").value("WHITE"))
                .andExpect(jsonPath("$[1].label").value("Blanc"))
                .andExpect(jsonPath("$[3].value").value("GREY"))
                .andExpect(jsonPath("$[3].label").value("Gris"));
    }

    /**
     * Chaîne complète du vestiaire : l'organisateur choisit jusqu'à 3 couleurs
     * (un pagne combine souvent plusieurs teintes), et l'invité les reçoit sur
     * la page publique avec libellé et couleur d'aperçu (sans JWT).
     */
    @Test
    void publicInvitation_exposesUpToThreeDressColors() throws Exception {
        CreateEventRequest req = weddingRequest();
        req.setDressColors(java.util.List.of(
                EventDressCode.BLACK, EventDressCode.IVORY, EventDressCode.GREY));
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
                .andExpect(jsonPath("$.dressColors", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.dressColors[0].value").value("BLACK"))
                .andExpect(jsonPath("$.dressColors[0].label").value("Noir"))
                .andExpect(jsonPath("$.dressColors[0].hex").value("#1F1F1F"))
                .andExpect(jsonPath("$.dressColors[1].value").value("IVORY"))
                .andExpect(jsonPath("$.dressColors[1].label").value("Blanc cassé / Ivoire"))
                .andExpect(jsonPath("$.dressColors[2].value").value("GREY"))
                .andExpect(jsonPath("$.dressColors[2].label").value("Gris"));
    }

    /** Maximum 3 couleurs : au-delà, la requête est refusée (400). */
    @Test
    void create_withMoreThanThreeDressColors_returns400() throws Exception {
        CreateEventRequest req = weddingRequest();
        req.setDressColors(java.util.List.of(
                EventDressCode.BLACK, EventDressCode.WHITE,
                EventDressCode.IVORY, EventDressCode.GREY));
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    /** PNG 1×1 valide : la détection ne regarde que les magic bytes. */
    private static byte[] pngBytes() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    /** Crée un mariage et renvoie son id (helper des tests « photo du pagne »). */
    private long createWeddingEvent() throws Exception {
        String body = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weddingRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    /**
     * Photo du pagne / tissu de tenue (mariages coutumiers) : l'organisateur
     * l'upload, l'invité la lit SANS JWT sur la page d'invitation, puis la
     * suppression la rend inaccessible. Chaîne complète du vestiaire.
     */
    @Test
    void dressImage_upload_publicRead_delete_lifecycle() throws Exception {
        long eventId = createWeddingEvent();

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                        "/api/events/{id}/dress-image", eventId)
                        .file(new MockMultipartFile("file", "pagne.png", "image/png", pngBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Le drapeau remonte dans la réponse administrative (sélecteur front).
        mockMvc.perform(get("/api/events/{id}", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDressImage").value(true));

        // Lecture publique : la page d'invitation n'a pas de JWT.
        mockMvc.perform(get("/api/events/{id}/dress-image", eventId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(pngBytes()));

        mockMvc.perform(delete("/api/events/{id}/dress-image", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}/dress-image", eventId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/{id}", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDressImage").value(false));
    }

    /** Format non reconnu (magic bytes absents) : 400 + message explicite. */
    @Test
    void dressImage_unsupportedFormat_returns400() throws Exception {
        long eventId = createWeddingEvent();
        byte[] notAnImage = "ceci n'est pas une image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                        "/api/events/{id}/dress-image", eventId)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", notAnImage))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    /** Sans photo, la page publique n'expose aucun lien ; après upload, l'URL y figure. */
    @Test
    void publicInvitation_exposesDressImageUrlOnlyWhenJoined() throws Exception {
        long eventId = createWeddingEvent();

        String guestBody = mockMvc.perform(post("/api/events/" + eventId + "/guests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Invite\",\"lastName\":\"Pagne\",\"phone\":\"2250701020400\",\"email\":\"pagne@test.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long guestId = objectMapper.readTree(guestBody).get("id").asLong();
        mockMvc.perform(post("/api/events/" + eventId + "/invitations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated());
        String publicToken = invitationRepository.findByWeddingId(eventId).get(0).getPublicToken();

        mockMvc.perform(get("/api/public/invitations/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dressImageUrl").doesNotExist());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                        "/api/events/{id}/dress-image", eventId)
                        .file(new MockMultipartFile("file", "pagne.png", "image/png", pngBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/public/invitations/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dressImageUrl")
                        .value("/api/events/" + eventId + "/dress-image"));
    }
}


