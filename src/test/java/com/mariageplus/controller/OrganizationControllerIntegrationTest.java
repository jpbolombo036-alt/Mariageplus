package com.mariageplus.controller;

import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolation multi-tenant : un ORGANISATEUR ne doit jamais accéder aux données
 * d'une autre organisation (ni à la liste globale des organisations).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrganizationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    private static Long orgAId;
    private static Long orgBId;
    private static String tokenA;
    private static boolean initialized;

    private RegisterRequest buildRequest(String suffix) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Org");
        req.setLastName(suffix);
        req.setEmail("organisateur-" + suffix.toLowerCase() + "@example.com");
        req.setPassword("password123");
        req.setOrganizationName("Organisation " + suffix);
        return req;
    }

    @BeforeEach
    void setUp() {
        // Le contexte Spring et la base H2 sont partagés entre les tests : on
        // n'enregistre les deux organisateurs qu'une seule fois.
        if (!initialized) {
            LoginResponse orgA = authService.register(buildRequest("A"));
            LoginResponse orgB = authService.register(buildRequest("B"));
            orgAId = orgA.getUser().getOrganizationId();
            orgBId = orgB.getUser().getOrganizationId();
            tokenA = orgA.getAccessToken();
            initialized = true;
        }
    }

    @Test
    void organizerA_ShouldAccessOwnOrganization() throws Exception {
        mockMvc.perform(get("/api/organizations/{id}", orgAId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void organizerA_ShouldBeForbidden_FromAnotherOrganization() throws Exception {
        mockMvc.perform(get("/api/organizations/{id}", orgBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_ShouldBeForbidden_FromAnotherOrganizationMembers() throws Exception {
        mockMvc.perform(get("/api/organizations/{id}/members", orgBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerA_ShouldBeForbidden_FromGlobalOrganizationList() throws Exception {
        mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_ShouldBeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/organizations/{id}", orgAId))
                .andExpect(status().isUnauthorized());
    }
}