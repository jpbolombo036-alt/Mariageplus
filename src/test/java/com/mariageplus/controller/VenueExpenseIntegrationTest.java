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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dépenses de salle : le caissier saisit et ne voit que les siennes, le gérant
 * voit tout, modifie et annule ; « Ma caisse » et le tableau de bord en tiennent compte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VenueExpenseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;

    private String owner;
    private String agent;
    private long venueId;
    private final String today = LocalDate.now().toString();

    @BeforeEach
    void setUp() throws Exception {
        LoginResponse organizer = register("Salle Depenses");
        owner = organizer.getAccessToken();
        long orgId = organizer.getUser().getOrganizationId();
        organizationSettingsService.setVenueModuleEnabled(orgId, true);

        venueId = json(perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Salle Depenses"))), owner, 201)).path("id").asLong();

        String email = "caissier-" + UUID.randomUUID() + "@example.com";
        perform(post("/api/organizations/{id}/members", orgId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("firstName", "Caissier", "lastName", "Depense", "email", email,
                        "password", "password123", "roleCode", "AGENT_SALLE"))), owner, 201);
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        agent = authService.login(login).getAccessToken();
    }

    @Test
    void cashierRecords_managerSeesAll_editsAndCancels() throws Exception {
        long agentExpense = json(perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("CARBURANT", "Gasoil groupe", 40, "USD", "ESPECES", venueId))), agent, 201)).path("id").asLong();
        perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("ELECTRICITE", "Facture SNEL", 150000, "CDF", "MPESA", null))), owner, 201);

        // Le caissier ne voit que sa dépense ; le gérant voit les deux, avec les totaux par devise et par poste.
        JsonNode mine = json(perform(get("/api/venues/expenses"), agent, 200));
        assertThat(mine.path("allRecorders").asBoolean()).isFalse();
        assertThat(mine.path("items")).hasSize(1);
        assertThat(mine.path("items").get(0).path("venueName").asText()).isEqualTo("Salle Depenses");

        JsonNode all = json(perform(get("/api/venues/expenses"), owner, 200));
        assertThat(all.path("allRecorders").asBoolean()).isTrue();
        assertThat(all.path("items")).hasSize(2);
        assertThat(amount(all.path("total"), "USD")).isEqualTo(40.0);
        assertThat(amount(all.path("total"), "CDF")).isEqualTo(150000.0);
        assertThat(all.path("byCategory")).hasSize(2);
        assertThat(json(perform(get("/api/venues/expenses").param("category", "CARBURANT"), owner, 200)).path("items")).hasSize(1);

        // Modifier et annuler : gérant seulement.
        perform(put("/api/venues/expenses/{id}", agentExpense).contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("CARBURANT", "Gasoil", 45, "USD", "ESPECES", venueId))), agent, 403);
        perform(delete("/api/venues/expenses/{id}", agentExpense), agent, 403);
        JsonNode updated = json(perform(put("/api/venues/expenses/{id}", agentExpense).contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("CARBURANT", "Gasoil (corrigé)", 45, "USD", "ESPECES", venueId))), owner, 200));
        assertThat(updated.path("amount").asDouble()).isEqualTo(45.0);
        assertThat(updated.path("recordedByName").asText()).isEqualTo("Caissier Depense");

        perform(delete("/api/venues/expenses/{id}", agentExpense).param("reason", "Doublon"), owner, 204);
        assertThat(json(perform(get("/api/venues/expenses"), owner, 200)).path("items")).hasSize(1);
        assertThat(json(perform(get("/api/venues/expenses"), agent, 200)).path("items")).isEmpty();
    }

    @Test
    void cashExpensesReduceMyCash_andTheDashboardShowsTheResult() throws Exception {
        long bookingId = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 1000, "startAt", "2027-08-10T10:00:00", "endAt", "2027-08-10T22:00:00",
                "client", Map.of("lastName", "Client")))), agent, 201)).path("id").asLong();
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 300, "method", "ESPECES"))), agent, 201);
        perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("NETTOYAGE", "Équipe de nettoyage", 50, "USD", "ESPECES", venueId))), agent, 201);
        perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("SECURITE", "Gardiennage", 20, "USD", "MPESA", null))), owner, 201);

        JsonNode cash = json(perform(get("/api/venues/my-cash"), agent, 200));
        assertThat(amount(cash.path("collected"), "USD")).isEqualTo(300.0);
        assertThat(amount(cash.path("spent"), "USD")).isEqualTo(50.0);
        assertThat(amount(cash.path("todaySpent"), "USD")).isEqualTo(50.0);
        assertThat(amount(cash.path("balance"), "USD")).isEqualTo(250.0);
        assertThat(cash.path("expenseCount").asLong()).isEqualTo(1);
        JsonNode especes = null;
        for (JsonNode m : cash.path("byMethod")) {
            if ("ESPECES".equals(m.path("method").asText()) && "USD".equals(m.path("currency").asText())) especes = m;
        }
        assertThat(especes).isNotNull();
        assertThat(especes.path("amount").asDouble()).as("espèces en caisse").isEqualTo(250.0);

        JsonNode dashboard = json(perform(get("/api/venues/dashboard"), owner, 200));
        assertThat(amount(dashboard.path("collected"), "USD")).isEqualTo(300.0);
        assertThat(amount(dashboard.path("spent"), "USD")).isEqualTo(70.0);
        assertThat(amount(dashboard.path("result"), "USD")).isEqualTo(230.0);
        assertThat(dashboard.path("expenseCount").asLong()).isEqualTo(2);
        assertThat(dashboard.path("expensesByCategory").get(0).path("category").asText()).isEqualTo("NETTOYAGE");
        JsonNode cashier = null;
        for (JsonNode c : dashboard.path("byCashier")) {
            if ("Caissier Depense".equals(c.path("name").asText())) cashier = c;
        }
        assertThat(cashier).isNotNull();
        assertThat(amount(cashier.path("cashInHand"), "USD")).isEqualTo(250.0);

        // Filtré par salle : la dépense générale (sans salle) n'est pas comptée.
        JsonNode byVenue = json(perform(get("/api/venues/dashboard").param("venueId", String.valueOf(venueId)), owner, 200));
        assertThat(amount(byVenue.path("spent"), "USD")).isEqualTo(50.0);

        // Le caissier n'a pas accès au tableau de bord.
        perform(get("/api/venues/dashboard"), agent, 403);
    }

    @Test
    void invalidExpensesAreRejected() throws Exception {
        perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("AUTRE", "Rien", 0, "USD", "ESPECES", null))), owner, 400);
        perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("AUTRE", " ", 10, "USD", "ESPECES", null))), owner, 400);

        // Salle d'une autre organisation.
        LoginResponse other = register("Autre Salle");
        organizationSettingsService.setVenueModuleEnabled(other.getUser().getOrganizationId(), true);
        long otherVenue = json(perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Autre"))), other.getAccessToken(), 201)).path("id").asLong();
        perform(post("/api/venues/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(expense("AUTRE", "Intrus", 10, "USD", "ESPECES", otherVenue))), owner, 404);
    }

    private Map<String, Object> expense(String category, String label, double amount, String currency, String method, Long venue) {
        Map<String, Object> m = new HashMap<>();
        m.put("category", category);
        m.put("label", label);
        m.put("amount", amount);
        m.put("currency", currency);
        m.put("method", method);
        m.put("spentAt", today + "T09:30:00");
        if (venue != null) m.put("venueId", venue);
        return m;
    }

    private static double amount(JsonNode list, String currency) {
        for (JsonNode n : list) {
            if (currency.equals(n.path("currency").asText())) return n.path("amount").asDouble();
        }
        return 0.0;
    }

    private LoginResponse register(String organization) {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Gerant");
        r.setLastName("Salle");
        r.setEmail("gerant-" + UUID.randomUUID() + "@example.com");
        r.setPassword("password123");
        r.setOrganizationName(organization);
        return authService.register(r);
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
