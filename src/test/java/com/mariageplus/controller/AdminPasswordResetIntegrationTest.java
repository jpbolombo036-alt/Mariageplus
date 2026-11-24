package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminPasswordResetIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    @Test
    void superAdminCanResetAnotherUserPasswordAndRevokeSessions() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        LoginResponse target = registerTarget();
        String body = objectMapper.writeValueAsString(Map.of("newPassword", "NouveauMotDePasse123"));

        mockMvc.perform(put("/api/users/{id}/password", target.getUser().getId())
                        .header("Authorization", "Bearer " + target.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/{id}/password", target.getUser().getId())
                        .header("Authorization", "Bearer " + admin.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + target.getAccessToken()))
                .andExpect(status().isUnauthorized());
        assertThrows(ResourceNotFoundException.class,
                () -> authService.refreshToken(target.getRefreshToken()));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("reset-target@example.com", "AncienMotDePasse123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("reset-target@example.com", "NouveauMotDePasse123")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/{id}/password", admin.getUser().getId())
                        .header("Authorization", "Bearer " + admin.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordRejectsPasswordsShorterThanEightCharacters() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        LoginResponse target = registerTargetWithEmail("reset-short-password@example.com");

        mockMvc.perform(put("/api/users/{id}/password", target.getUser().getId())
                        .header("Authorization", "Bearer " + admin.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "court"))))
                .andExpect(status().isBadRequest());
    }

    private LoginResponse registerTarget() {
        return registerTargetWithEmail("reset-target@example.com");
    }

    private LoginResponse registerTargetWithEmail(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Compte");
        request.setLastName("À réinitialiser");
        request.setEmail(email);
        request.setPassword("AncienMotDePasse123");
        request.setOrganizationName("Organisation " + email);
        return authService.register(request);
    }

    private LoginResponse login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return authService.login(request);
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }
}
