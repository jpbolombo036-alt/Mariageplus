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
 * Rôle AGENT_SALLE (caissier) : il réserve et encaisse sur toutes les salles de
 * l'organisation, mais ne rembourse pas, n'annule pas de paiement, ne voit pas
 * les rapports et ne gère ni les salles, ni le taux du jour, ni les accès clients.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VenueAgentRoleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;

    private String owner;
    private String agent;
    private long venueId;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Gerant");
        r.setLastName("Salle");
        r.setEmail("gerant-" + UUID.randomUUID() + "@example.com");
        r.setPassword("password123");
        r.setOrganizationName("Salle Agent");
        LoginResponse organizer = authService.register(r);
        owner = organizer.getAccessToken();
        long orgId = organizer.getUser().getOrganizationId();
        organizationSettingsService.setVenueModuleEnabled(orgId, true);
        organizationSettingsService.setEventCreationEnabled(orgId, true);

        venueId = json(perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Salle Agent"))), owner, 201)).path("id").asLong();

        String email = "caissier-" + UUID.randomUUID() + "@example.com";
        JsonNode member = json(perform(post("/api/organizations/{id}/members", orgId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("firstName", "Caissier", "lastName", "Salle", "email", email,
                        "password", "password123", "roleCode", "AGENT_SALLE"))), owner, 201));
        assertThat(member.path("roleCode").asText()).isEqualTo("AGENT_SALLE");
        assertThat(member.path("weddingId").isNull()).as("agent de salle : toute l'organisation").isTrue();

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        agent = authService.login(login).getAccessToken();
    }

    @Test
    void agent_booksAndCollects_onAllVenues() throws Exception {
        perform(get("/api/venues"), agent, 200);
        perform(get("/api/venues/{id}/calendar", venueId), agent, 200);
        perform(get("/api/venues/exchange-rate"), agent, 200);

        long bookingId = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 800, "startAt", "2027-04-10T10:00:00", "endAt", "2027-04-10T22:00:00",
                "client", Map.of("lastName", "ClientAgent", "phone", "0812000000")))), agent, 201)).path("id").asLong();
        perform(post("/api/bookings/{id}/extras", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("label", "Chaises", "quantity", 10, "unitPrice", 2))), agent, 201);
        long paymentId = json(perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 300, "method", "MPESA"))), agent, 201)).path("id").asLong();
        perform(get("/api/bookings/{id}/payments/{pid}/receipt.pdf", bookingId, paymentId), agent, 200);
        perform(get("/api/bookings/{id}/quote.pdf", bookingId), agent, 200);
        perform(post("/api/bookings/{id}/confirm", bookingId), agent, 200);
        perform(get("/api/venue-clients"), agent, 200);

        // Sorties d'argent : réservées au gérant.
        perform(delete("/api/bookings/{id}/payments/{pid}", bookingId, paymentId), agent, 403);
        perform(post("/api/bookings/{id}/cancel", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("reason", "test", "refund", Map.of("amount", 100, "method", "ESPECES")))), agent, 403);
        // L'annulation refusée n'a rien changé.
        assertThat(json(perform(get("/api/bookings/{id}", bookingId), owner, 200)).path("status").asText()).isEqualTo("CONFIRMEE");
        // Annuler sans rembourser reste possible ; le gérant rembourse ensuite.
        perform(post("/api/bookings/{id}/cancel", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("reason", "Client empêché"))), agent, 200);
        perform(post("/api/bookings/{id}/refunds", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 100, "method", "ESPECES"))), agent, 403);
        perform(post("/api/bookings/{id}/refunds", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 100, "method", "ESPECES"))), owner, 201);
    }

    @Test
    void myCash_showsOnlyWhatEachPersonCollected() throws Exception {
        perform(post("/api/venues/exchange-rate").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("cdfPerUsd", 2800))), owner, 200);
        long bookingId = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 2000, "startAt", "2027-06-10T10:00:00", "endAt", "2027-06-10T22:00:00",
                "client", Map.of("lastName", "Caisse")))), agent, 201)).path("id").asLong();

        // Le caissier encaisse 300 USD en espèces et 280 000 CDF par M-Pesa ; le gérant 100 USD.
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 300, "method", "ESPECES"))), agent, 201);
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 280000, "currency", "CDF", "method", "MPESA", "reference", "MP-77"))), agent, 201);
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 100, "method", "ESPECES"))), owner, 201);

        JsonNode cash = json(perform(get("/api/venues/my-cash"), agent, 200));
        assertThat(amount(cash.path("today"), "USD")).isEqualByComparingTo("300");
        assertThat(amount(cash.path("today"), "CDF")).isEqualByComparingTo("280000");
        assertThat(amount(cash.path("collected"), "USD")).isEqualByComparingTo("300");
        assertThat(cash.path("paymentCount").asLong()).isEqualTo(2);
        assertThat(cash.path("movements")).hasSize(2);
        assertThat(cash.path("movements").toString()).contains("Caisse").contains("MP-77");
        assertThat(cash.path("byMethod").toString()).contains("ESPECES").contains("MPESA");
        assertThat(cash.path("upcoming")).isNotNull();

        // Le gérant ne voit dans « Ma caisse » que ses propres 100 USD.
        JsonNode ownerCash = json(perform(get("/api/venues/my-cash"), owner, 200));
        assertThat(amount(ownerCash.path("collected"), "USD")).isEqualByComparingTo("100");
        assertThat(amount(ownerCash.path("collected"), "CDF")).isEqualByComparingTo("0");
        assertThat(ownerCash.path("movements")).hasSize(1);

        // Tableau de bord du gérant : encaissements par personne, avec la part espèces / Mobile Money.
        JsonNode byCashier = json(perform(get("/api/venues/dashboard"), owner, 200)).path("byCashier");
        assertThat(byCashier).hasSize(2);
        JsonNode cashier = null;
        JsonNode manager = null;
        for (JsonNode c : byCashier) {
            if (c.path("name").asText().startsWith("Caissier")) cashier = c;
            if (c.path("name").asText().startsWith("Gerant")) manager = c;
        }
        assertThat(cashier).as("le caissier apparaît").isNotNull();
        assertThat(manager).as("le gérant apparaît").isNotNull();
        assertThat(cashier.path("paymentCount").asLong()).isEqualTo(2);
        assertThat(amount(cashier.path("today"), "USD")).isEqualByComparingTo("300");
        assertThat(amount(cashier.path("cash"), "USD")).isEqualByComparingTo("300");
        assertThat(amount(cashier.path("mobileMoney"), "CDF")).isEqualByComparingTo("280000");
        assertThat(amount(cashier.path("cash"), "CDF")).isEqualByComparingTo("0");
        assertThat(amount(manager.path("collected"), "USD")).isEqualByComparingTo("100");
        // Le caissier, lui, n'a pas accès à ce tableau de bord.
        perform(get("/api/venues/dashboard"), agent, 403);
    }

    private java.math.BigDecimal amount(JsonNode list, String currency) {
        for (JsonNode n : list) {
            if (currency.equals(n.path("currency").asText())) {
                return n.path("amount").decimalValue();
            }
        }
        return java.math.BigDecimal.ZERO;
    }

    @Test
    void agent_cannotSeeReports_norManageVenuesRatesEventsOrClientAccess() throws Exception {
        perform(get("/api/venues/dashboard"), agent, 403);
        perform(get("/api/venues/export.xlsx"), agent, 403);
        perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Interdite"))), agent, 403);
        perform(put("/api/venues/{id}", venueId).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Renommée"))), agent, 403);
        perform(post("/api/venues/{id}/rates", venueId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("label", "Promo", "price", 1, "currency", "USD"))), agent, 403);
        perform(post("/api/venues/exchange-rate").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("cdfPerUsd", 1000))), agent, 403);

        long bookingId = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 500, "startAt", "2027-05-10T10:00:00", "endAt", "2027-05-10T22:00:00",
                "client", Map.of("lastName", "AutreClient", "email", "autre-" + UUID.randomUUID() + "@example.com")))), agent, 201))
                .path("id").asLong();
        perform(post("/api/bookings/{id}/confirm", bookingId), agent, 200);
        perform(post("/api/bookings/{id}/event", bookingId).contentType(MediaType.APPLICATION_JSON).content("{}"), agent, 403);
        // Le gérant crée l'événement ; l'agent ne peut toujours pas créer de compte client.
        perform(post("/api/bookings/{id}/event", bookingId).contentType(MediaType.APPLICATION_JSON).content("{}"), owner, 201);
        perform(post("/api/bookings/{id}/client-access", bookingId).contentType(MediaType.APPLICATION_JSON).content("{}"), agent, 403);
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
