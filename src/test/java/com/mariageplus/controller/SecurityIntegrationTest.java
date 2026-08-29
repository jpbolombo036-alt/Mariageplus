package com.mariageplus.controller;

import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 10 — Sécurité : les endpoints protégés exigent un JWT valide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    @Test
    void protectedEndpoint_withoutJwt_401() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_invalidJwt_401() throws Exception {
        mockMvc.perform(get("/api/events").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_tamperedJwt_401() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Sec");
        req.setLastName("User");
        req.setEmail("security-journey@example.com");
        req.setPassword("password123");
        req.setOrganizationName("Organisation Sécurité");
        String token = authService.register(req).getAccessToken() + "tampered"; // signature altérée

        mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_revokedJwt_401() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Revoked");
        req.setLastName("User");
        req.setEmail("revoked-token@example.com");
        req.setPassword("password123");
        req.setOrganizationName("Organisation Token");
        LoginResponse response = authService.register(req);

        authService.logout(response.getUser().getId());

        mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + response.getAccessToken()))
                .andExpect(status().isUnauthorized());
    }
}
