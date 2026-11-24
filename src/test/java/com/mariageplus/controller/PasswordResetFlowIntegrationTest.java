package com.mariageplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.default-country-code=243")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetFlowIntegrationTest {

    private static final String NEUTRAL =
            "Si un compte correspond à cette adresse, notre équipe a été prévenue et vous contactera.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    @Test
    void forgottenPasswordIsHandledThroughTheSuperAdminWithoutSmtp() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        LoginResponse user = register("oubli@example.com", "0812345678", "AncienMotDePasse123");
        long userId = user.getUser().getId();

        // 1. Same answer whether or not the account exists.
        requestReset("inconnu@example.com").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(NEUTRAL));
        requestReset("oubli@example.com").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(NEUTRAL));
        requestReset("oubli@example.com").andExpect(status().isAccepted());
        requestReset("pas-un-email").andExpect(status().isBadRequest());

        JsonNode bell = json(mockMvc.perform(get("/api/admin/notifications").header("Authorization", bearer(admin))));
        long requests = StreamSupport.stream(bell.get("items").spliterator(), false)
                .filter(n -> "PASSWORD_RESET_REQUEST".equals(n.get("type").asText()))
                .peek(n -> assertEquals(userId, n.get("userId").asLong()))
                .peek(n -> assertTrue(n.get("message").asText().contains("0812345678")))
                .count();
        assertEquals(1, requests, "an unread request is not duplicated, unknown emails create nothing");

        // 2. Only a SUPER_ADMIN issues a temporary password; the user's sessions are revoked.
        mockMvc.perform(post("/api/users/{id}/temporary-password", userId).header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        JsonNode temporary = json(mockMvc.perform(post("/api/users/{id}/temporary-password", userId)
                .header("Authorization", bearer(admin))));
        String temporaryPassword = temporary.get("temporaryPassword").asText();
        assertTrue(temporaryPassword.matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}-[A-Za-z2-9]{4}"), temporaryPassword);
        assertEquals("243812345678", temporary.get("whatsappNumber").asText());
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user))).andExpect(status().isUnauthorized());

        // 3. With the temporary password, only the password change is allowed.
        LoginResponse temporarySession = login("oubli@example.com", temporaryPassword);
        assertTrue(temporarySession.getUser().isMustChangePassword());
        mockMvc.perform(get("/api/events").header("Authorization", bearer(temporarySession)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(temporarySession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        changePassword(temporarySession, temporaryPassword, temporaryPassword).andExpect(status().isBadRequest());
        changePassword(temporarySession, temporaryPassword, "NouveauMotDePasse123").andExpect(status().isOk());

        mockMvc.perform(get("/api/events").header("Authorization", bearer(temporarySession)))
                .andExpect(status().isOk());
        assertFalse(login("oubli@example.com", "NouveauMotDePasse123").getUser().isMustChangePassword());
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/auth/password-reset-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions changePassword(LoginResponse who, String oldPassword, String newPassword) throws Exception {
        return mockMvc.perform(put("/api/users/me/password").header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oldPassword", oldPassword, "newPassword", newPassword))));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private LoginResponse register(String email, String phone, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Oubli");
        request.setLastName("Test");
        request.setEmail(email);
        request.setPhone(phone);
        request.setPassword(password);
        request.setOrganizationName("Organisation " + email);
        return authService.register(request);
    }

    private LoginResponse login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return authService.login(request);
    }

    private static String bearer(LoginResponse login) {
        return "Bearer " + login.getAccessToken();
    }
}
