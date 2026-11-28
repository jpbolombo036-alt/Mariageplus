package com.mariageplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** En-tête des devis et reçus : mentions légales, coordonnées, pied de page et logo dans les PDF. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VenueDocumentSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private OrganizationSettingsService organizationSettingsService;

    private String owner;
    private String agent;
    private long bookingId;
    private long paymentId;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Gerant");
        r.setLastName("Docs");
        r.setEmail("docs-" + UUID.randomUUID() + "@example.com");
        r.setPassword("password123");
        r.setOrganizationName("Salle Docs");
        LoginResponse organizer = authService.register(r);
        owner = organizer.getAccessToken();
        long orgId = organizer.getUser().getOrganizationId();
        organizationSettingsService.setVenueModuleEnabled(orgId, true);

        long venueId = json(perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Salle Exodus"))), owner, 201)).path("id").asLong();
        bookingId = json(perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "venueId", venueId, "basePrice", 1000, "startAt", "2027-08-01T10:00:00", "endAt", "2027-08-01T22:00:00",
                "client", Map.of("lastName", "Docs")))), owner, 201)).path("id").asLong();
        paymentId = json(perform(post("/api/bookings/{id}/payments", bookingId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", 400, "method", "ESPECES"))), owner, 201)).path("id").asLong();

        String email = "caisse-docs-" + UUID.randomUUID() + "@example.com";
        perform(post("/api/organizations/{id}/members", orgId).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("firstName", "Caissier", "lastName", "Docs", "email", email,
                        "password", "password123", "roleCode", "AGENT_SALLE"))), owner, 201);
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        agent = authService.login(login).getAccessToken();
    }

    @Test
    void legalMentions_andLogo_appearOnQuoteAndReceipt() throws Exception {
        Map<String, Object> settings = new HashMap<>();
        settings.put("legalName", "Exodus Events SARL");
        settings.put("rccm", "CD/BKV/RCCM/24-B-00123");
        settings.put("idNat", "5-93-N12345A");
        settings.put("nif", "A2412345B");
        settings.put("address", "55 bis, av. Lurhala, Bukavu");
        settings.put("phone", "+243 813 232 000");
        settings.put("footerNote", "Acompte non remboursable en cas d'annulation à moins de 30 jours.");
        JsonNode saved = json(perform(put("/api/venues/document-settings").contentType(MediaType.APPLICATION_JSON)
                .content(body(settings)), owner, 200));
        assertThat(saved.path("rccm").asText()).isEqualTo("CD/BKV/RCCM/24-B-00123");
        assertThat(saved.path("hasLogo").asBoolean()).isFalse();

        // Logo : PNG accepté, GIF refusé (non lu par le générateur PDF).
        perform(multipart(HttpMethod.PUT, "/api/venues/document-settings/logo")
                .file(new MockMultipartFile("file", "logo.gif", "image/gif", "GIF89a-not-really-an-image".getBytes())), owner, 400);
        JsonNode withLogo = json(perform(multipart(HttpMethod.PUT, "/api/venues/document-settings/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png())), owner, 200));
        assertThat(withLogo.path("hasLogo").asBoolean()).isTrue();
        perform(get("/api/venues/document-settings/logo"), owner, 200);

        byte[] quote = perform(get("/api/bookings/{id}/quote.pdf", bookingId), owner, 200).getResponse().getContentAsByteArray();
        String quoteText = pdfText(quote);
        assertThat(quoteText).contains("Exodus Events SARL", "RCCM : CD/BKV/RCCM/24-B-00123", "Id. Nat. : 5-93-N12345A",
                "NIF : A2412345B", "Bukavu", "Acompte non remboursable");
        assertThat(new String(quote, StandardCharsets.ISO_8859_1)).contains("/Subtype/Image");

        byte[] receipt = perform(get("/api/bookings/{id}/payments/{pid}/receipt.pdf", bookingId, paymentId), owner, 200)
                .getResponse().getContentAsByteArray();
        assertThat(pdfText(receipt)).contains("Exodus Events SARL", "NIF : A2412345B", "Acompte non remboursable");

        // Sans logo : le PDF ne contient plus d'image, le reste de l'en-tête demeure.
        perform(delete("/api/venues/document-settings/logo"), owner, 200);
        byte[] noLogo = perform(get("/api/bookings/{id}/quote.pdf", bookingId), owner, 200).getResponse().getContentAsByteArray();
        assertThat(new String(noLogo, StandardCharsets.ISO_8859_1)).doesNotContain("/Subtype/Image");
        assertThat(pdfText(noLogo)).contains("RCCM : CD/BKV/RCCM/24-B-00123");
    }

    @Test
    void cashierReadsButCannotChangeTheHeader() throws Exception {
        perform(get("/api/venues/document-settings"), agent, 200);
        perform(put("/api/venues/document-settings").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("legalName", "Pirate"))), agent, 403);
        perform(multipart(HttpMethod.PUT, "/api/venues/document-settings/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png())), agent, 403);
        // Les documents qu'il imprime portent quand même l'en-tête du gérant.
        perform(get("/api/bookings/{id}/quote.pdf", bookingId), agent, 200);
    }

    // ------------------------------------------------------------------

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(91, 46, 204));
        g.fillRect(0, 0, 120, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String pdfText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder text = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
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
