package com.mariageplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.entity.VenueBooking;
import com.mariageplus.entity.VenueBookingStatus;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.VenueBookingRepository;
import com.mariageplus.service.AuthService;
import com.mariageplus.service.OrganizationSettingsService;
import com.mariageplus.service.VenueBookingService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module « Salles » de bout en bout : activation par le SUPER_ADMIN, salles,
 * clients, réservations (anti double réservation, y compris en parallèle),
 * paiements USD/CDF, documents, rapports, événement lié et accès client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VenueModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;
    @Autowired private VenueBookingService venueBookingService;
    @Autowired private VenueBookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;

    private static boolean initialized;
    private static String tokenVenueOwner;
    private static String tokenOtherOwner;
    private static String tokenDisabledOrg;
    private static String superAdminToken;
    private static Long venueOrgId;
    private static Long disabledOrgId;

    @BeforeEach
    void setUp() throws Exception {
        if (initialized) {
            return;
        }
        LoginResponse owner = register("Salle Kinshasa");
        tokenVenueOwner = owner.getAccessToken();
        venueOrgId = owner.getUser().getOrganizationId();
        organizationSettingsService.setVenueModuleEnabled(venueOrgId, true);
        organizationSettingsService.setEventCreationEnabled(venueOrgId, true);

        LoginResponse other = register("Autre Salle");
        tokenOtherOwner = other.getAccessToken();
        organizationSettingsService.setVenueModuleEnabled(other.getUser().getOrganizationId(), true);

        LoginResponse disabled = register("Organisateur Simple");
        tokenDisabledOrg = disabled.getAccessToken();
        disabledOrgId = disabled.getUser().getOrganizationId();

        LoginRequest admin = new LoginRequest();
        admin.setEmail("admin@test.mariageplus.app");
        admin.setPassword("Admin@12345");
        superAdminToken = authService.login(admin).getAccessToken();
        initialized = true;
    }

    // ------------------------------------------------------------------
    // Activation du module
    // ------------------------------------------------------------------

    @Test
    void moduleDisabled_isForbidden_untilSuperAdminEnablesIt() throws Exception {
        assertThat(json(perform(get("/api/venues/module-status"), tokenDisabledOrg, 200)).path("enabled").asBoolean()).isFalse();
        perform(get("/api/venues"), tokenDisabledOrg, 403);

        // Un organisateur ne peut pas s'activer lui-même.
        perform(put("/api/admin/organizations/{id}/settings", disabledOrgId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"venueModuleEnabled\":true}"), tokenDisabledOrg, 403);

        JsonNode settings = json(perform(put("/api/admin/organizations/{id}/settings", disabledOrgId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"venueModuleEnabled\":true}"), superAdminToken, 200));
        assertThat(settings.path("effectiveVenueModule").asBoolean()).isTrue();
        perform(get("/api/venues"), tokenDisabledOrg, 200);

        perform(put("/api/admin/organizations/{id}/settings", disabledOrgId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"venueModuleEnabled\":false}"), superAdminToken, 200);
        perform(get("/api/venues"), tokenDisabledOrg, 403);
    }

    // ------------------------------------------------------------------
    // Parcours complet
    // ------------------------------------------------------------------

    @Test
    void fullJourney_booking_payments_documents_event_and_clientAccess() throws Exception {
        long venueId = createVenue("Salle Le Palmier", "1500");
        long rateId = json(perform(get("/api/venues/{id}", venueId), tokenVenueOwner, 200)).path("rates").get(0).path("id").asLong();

        LocalDateTime start = LocalDateTime.of(2027, 3, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2027, 3, 20, 23, 0);
        String email = "client-" + UUID.randomUUID() + "@example.com";

        // Réservation avec un nouveau client (téléphone local converti en +243).
        JsonNode booking = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "rateId", rateId, "title", "Mariage Ilunga",
                "eventType", "WEDDING", "startAt", start.toString(), "endAt", end.toString(), "guestCount", 300,
                "client", Map.of("firstName", "Patrick", "lastName", "Ilunga", "phone", "0812 345 678", "email", email)))),
                tokenVenueOwner, 201));
        long bookingId = booking.path("id").asLong();
        assertThat(booking.path("status").asText()).isEqualTo("OPTION");
        assertThat(booking.path("currency").asText()).isEqualTo("USD");
        assertThat(booking.path("totalAmount").decimalValue()).isEqualByComparingTo("1500");
        assertThat(booking.path("clientPhone").asText()).isEqualTo("+243812345678");
        assertThat(booking.path("optionExpiresAt").isNull()).isFalse();

        // Prestation : 2 x 100 USD.
        booking = json(perform(post("/api/bookings/{id}/extras", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("label", "Sonorisation", "quantity", 2, "unitPrice", 100))), tokenVenueOwner, 201));
        assertThat(booking.path("totalAmount").decimalValue()).isEqualByComparingTo("1700");

        // Chevauchement refusé ; créneau collé accepté.
        perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 800, "startAt", start.plusHours(5).toString(), "endAt", end.plusHours(5).toString(),
                "client", Map.of("lastName", "Concurrent")))), tokenVenueOwner, 409);
        perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 800, "startAt", end.toString(), "endAt", end.plusHours(10).toString(),
                "client", Map.of("lastName", "Lendemain")))), tokenVenueOwner, 201);

        // Paiements : 500 USD, puis 280 000 CDF au taux de 2 800.
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 500, "method", "ESPECES"))), tokenVenueOwner, 201);
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 280000, "currency", "CDF", "method", "MPESA"))), tokenVenueOwner, 400);
        JsonNode cdfPayment = json(perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 280000, "currency", "CDF", "exchangeRate", 2800, "method", "MPESA",
                        "reference", "MP-123"))), tokenVenueOwner, 201));
        assertThat(cdfPayment.path("amountInBookingCurrency").decimalValue()).isEqualByComparingTo("100");
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 5000, "method", "VIREMENT"))), tokenVenueOwner, 400);

        booking = json(perform(get("/api/bookings/{id}", bookingId), tokenVenueOwner, 200));
        assertThat(booking.path("paidAmount").decimalValue()).isEqualByComparingTo("600");
        assertThat(booking.path("balance").decimalValue()).isEqualByComparingTo("1100");
        assertThat(booking.path("payments")).hasSize(2);

        // Documents PDF.
        assertPdf(perform(get("/api/bookings/{id}/quote.pdf", bookingId), tokenVenueOwner, 200));
        assertPdf(perform(get("/api/bookings/{id}/payments/{pid}/receipt.pdf", bookingId, cdfPayment.path("id").asLong()),
                tokenVenueOwner, 200));

        // Événement : seulement une fois confirmée, noms des mariés requis.
        perform(post("/api/bookings/{id}/event", bookingId).contentType(MediaType.APPLICATION_JSON).content("{}"), tokenVenueOwner, 400);
        perform(post("/api/bookings/{id}/confirm", bookingId), tokenVenueOwner, 200);
        perform(post("/api/bookings/{id}/event", bookingId).contentType(MediaType.APPLICATION_JSON).content("{}"), tokenVenueOwner, 400);
        booking = json(perform(post("/api/bookings/{id}/event", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("weddingDetails", Map.of("groomFirstName", "Patrick", "groomLastName", "Ilunga",
                        "brideFirstName", "Grace", "brideLastName", "Mbuyi")))), tokenVenueOwner, 201));
        long eventId = booking.path("eventId").asLong();
        JsonNode event = json(perform(get("/api/events/{id}", eventId), tokenVenueOwner, 200));
        assertThat(event.path("eventDate").asText()).isEqualTo("2027-03-20");
        assertThat(event.path("venueName").asText()).isEqualTo("Salle Le Palmier");
        perform(post("/api/bookings/{id}/event", bookingId).contentType(MediaType.APPLICATION_JSON).content("{}"), tokenVenueOwner, 409);

        // Changement de date → événement synchronisé.
        perform(put("/api/bookings/{id}", bookingId).contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "startAt", start.plusDays(7).toString(), "endAt", end.plusDays(7).toString()))), tokenVenueOwner, 200);
        event = json(perform(get("/api/events/{id}", eventId), tokenVenueOwner, 200));
        assertThat(event.path("eventDate").asText()).isEqualTo("2027-03-27");

        // Accès client : GESTIONNAIRE_INVITES limité à son événement.
        JsonNode access = json(perform(post("/api/bookings/{id}/client-access", bookingId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), tokenVenueOwner, 201));
        assertThat(access.path("existingAccount").asBoolean()).isFalse();
        assertThat(access.path("whatsappNumber").asText()).isEqualTo("243812345678");
        String temporaryPassword = access.path("temporaryPassword").asText();
        assertThat(userRepository.findByEmail(email).orElseThrow().isMustChangePassword()).isTrue();

        LoginRequest clientLogin = new LoginRequest();
        clientLogin.setEmail(email);
        clientLogin.setPassword(temporaryPassword);
        String clientToken = authService.login(clientLogin).getAccessToken();
        // Mot de passe temporaire : tout est bloqué tant qu'il n'est pas changé.
        perform(get("/api/events/{id}/guests", eventId), clientToken, 403);
        perform(put("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "oldPassword", temporaryPassword, "newPassword", "NouveauMotDePasse2027!"))), clientToken, 200);
        clientLogin.setPassword("NouveauMotDePasse2027!");
        clientToken = authService.login(clientLogin).getAccessToken();
        perform(get("/api/events/{id}/guests", eventId), clientToken, 200);
        perform(get("/api/venues"), clientToken, 403);
        perform(get("/api/bookings/{id}", bookingId), clientToken, 403);
        perform(put("/api/events/{id}", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renommé par le client\"}"), clientToken, 403);

        // Rapports.
        JsonNode dashboard = json(perform(get("/api/venues/dashboard"), tokenVenueOwner, 200));
        // D'autres tests de la classe encaissent aussi en USD ce mois-ci : au moins nos 500 USD.
        assertThat(amount(dashboard.path("collected"), "USD")).isGreaterThanOrEqualTo(new java.math.BigDecimal("500"));
        assertThat(amount(dashboard.path("collected"), "CDF")).isEqualByComparingTo("280000");
        assertThat(amount(dashboard.path("outstanding"), "USD")).isGreaterThanOrEqualTo(new java.math.BigDecimal("1100"));
        JsonNode march = json(perform(get("/api/venues/dashboard?from=2027-03-01&to=2027-03-31&venueId=" + venueId),
                tokenVenueOwner, 200));
        assertThat(march.path("occupiedVenueDays").asLong()).isEqualTo(1);
        assertThat(march.path("availableVenueDays").asLong()).isEqualTo(31);
        MvcResult xlsx = perform(get("/api/venues/export.xlsx"), tokenVenueOwner, 200);
        assertThat(xlsx.getResponse().getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');

        JsonNode calendar = json(perform(get("/api/venues/{id}/calendar?month=2027-03", venueId), tokenVenueOwner, 200));
        assertThat(calendar.size()).isGreaterThanOrEqualTo(1);

        // Annulation → événement annulé, date libérée.
        perform(post("/api/bookings/{id}/cancel", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Changement de programme\"}"), tokenVenueOwner, 200);
        event = json(perform(get("/api/events/{id}", eventId), tokenVenueOwner, 200));
        assertThat(event.path("status").asText()).isEqualTo("CANCELLED");
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 10, "method", "ESPECES"))), tokenVenueOwner, 400);
        perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 900, "startAt", start.plusDays(7).toString(), "endAt", end.plusDays(7).toString(),
                "client", Map.of("lastName", "Remplaçant")))), tokenVenueOwner, 201);
    }

    // ------------------------------------------------------------------
    // Isolation, expiration, concurrence
    // ------------------------------------------------------------------

    @Test
    void otherOrganization_cannotSeeOrUseVenueOrBooking() throws Exception {
        long venueId = createVenue("Salle Privée", "700");
        long bookingId = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "startAt", "2027-05-01T10:00:00", "endAt", "2027-05-01T18:00:00",
                "client", Map.of("lastName", "Kabila")))), tokenVenueOwner, 201)).path("id").asLong();

        perform(get("/api/venues/{id}", venueId), tokenOtherOwner, 403);
        perform(get("/api/bookings/{id}", bookingId), tokenOtherOwner, 403);
        perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 10, "method", "ESPECES"))), tokenOtherOwner, 403);
        perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "startAt", "2027-06-01T10:00:00", "endAt", "2027-06-01T18:00:00",
                "client", Map.of("lastName", "Intrus")))), tokenOtherOwner, 403);
        JsonNode otherList = json(perform(get("/api/bookings"), tokenOtherOwner, 200));
        assertThat(otherList.path("content").toString()).doesNotContain("Kabila");
    }

    @Test
    void expiredOption_withoutDeposit_isReleased_butNotWithDeposit() throws Exception {
        long venueId = createVenue("Salle Option", "300");
        long freeOption = createBooking(venueId, "2027-07-01T10:00:00", "2027-07-01T20:00:00", "Sans Acompte");
        long paidOption = createBooking(venueId, "2027-07-02T10:00:00", "2027-07-02T20:00:00", "Avec Acompte");
        perform(post("/api/bookings/{id}/payments", paidOption).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 50, "method", "AIRTEL_MONEY"))), tokenVenueOwner, 201);

        for (long id : List.of(freeOption, paidOption)) {
            VenueBooking b = bookingRepository.findById(id).orElseThrow();
            b.setOptionExpiresAt(LocalDateTime.now().minusHours(1));
            bookingRepository.save(b);
        }
        venueBookingService.expireOptions();

        assertThat(bookingRepository.findById(freeOption).orElseThrow().getStatus()).isEqualTo(VenueBookingStatus.ANNULEE);
        assertThat(bookingRepository.findById(paidOption).orElseThrow().getStatus()).isEqualTo(VenueBookingStatus.OPTION);
        createBooking(venueId, "2027-07-01T10:00:00", "2027-07-01T20:00:00", "Nouveau Client");
    }

    @Test
    void concurrentBookingsOfSameSlot_onlyOneSucceeds() throws Exception {
        long venueId = createVenue("Salle Concurrence", "1000");
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String name = "Parallele" + i;
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer " + tokenVenueOwner)
                                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                                        "venueId", venueId, "startAt", "2027-08-15T12:00:00", "endAt", "2027-08-15T23:00:00",
                                        "client", Map.of("lastName", name)))))
                        .andReturn().getResponse().getStatus();
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private LoginResponse register(String orgName) {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Gerant");
        r.setLastName(orgName);
        r.setEmail("venue-" + UUID.randomUUID() + "@example.com");
        r.setPassword("password123");
        r.setOrganizationName(orgName);
        return authService.register(r);
    }

    private long createVenue(String name, String dayPrice) throws Exception {
        long venueId = json(perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "name", name, "address", "Avenue de la Justice 12", "commune", "Gombe", "city", "Kinshasa",
                "country", "RDC", "capacitySeated", 400))), tokenVenueOwner, 201)).path("id").asLong();
        perform(post("/api/venues/{id}/rates", venueId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("label", "Journée", "price", dayPrice, "currency", "USD"))), tokenVenueOwner, 201);
        return venueId;
    }

    private long createBooking(long venueId, String start, String end, String clientName) throws Exception {
        return json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 300, "startAt", start, "endAt", end,
                "client", Map.of("lastName", clientName)))), tokenVenueOwner, 201)).path("id").asLong();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void assertPdf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
    }

    private java.math.BigDecimal amount(JsonNode list, String currency) {
        for (JsonNode n : list) {
            if (currency.equals(n.path("currency").asText())) {
                return n.path("amount").decimalValue();
            }
        }
        return java.math.BigDecimal.ZERO;
    }
}
