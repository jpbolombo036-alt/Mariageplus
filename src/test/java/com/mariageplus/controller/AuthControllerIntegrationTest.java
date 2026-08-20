package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.entity.User;
import com.mariageplus.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void register_ShouldCreateOrganizerAndReturnToken() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Grâce");
        request.setLastName("Mwamba");
        request.setEmail("grace@example.com");
        request.setPhone("+243900000001");
        request.setPassword("password123");
        request.setOrganizationName("Agence Grâce Events");

        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("grace@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("ORGANISATEUR"))
                .andReturn().getResponse().getContentAsString();

        LoginResponse response = objectMapper.readValue(body, LoginResponse.class);
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
    }

    @Test
    void login_ShouldReturnToken_ForActiveUser() throws Exception {
        userRepository.findByEmail("login-active@example.com").ifPresent(userRepository::delete);
        userRepository.save(User.builder()
                .firstName("Actif")
                .lastName("User")
                .email("login-active@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .active(true)
                .build());

        LoginRequest login = new LoginRequest();
        login.setEmail("login-active@example.com");
        login.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void login_ShouldRejectDisabledUser() throws Exception {
        userRepository.findByEmail("disabled@example.com").ifPresent(userRepository::delete);
        userRepository.save(User.builder()
                .firstName("Désactivé")
                .lastName("User")
                .email("disabled@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .active(false)
                .build());

        LoginRequest login = new LoginRequest();
        login.setEmail("disabled@example.com");
        login.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_ShouldTemporarilyLockAccount_AfterFiveFailedAttempts() throws Exception {
        String email = "login-lockout@example.com";
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
        userRepository.save(User.builder()
                .firstName("Lockout")
                .lastName("User")
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .active(true)
                .build());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("wrong-password");
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isUnauthorized());
        }

        User lockedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(lockedUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(lockedUser.getLockedUntil()).isAfter(java.time.LocalDateTime.now());

        login.setPassword("password123");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_ShouldReturnNewTokens_WhenValidRefreshToken() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Refresh");
        request.setLastName("User");
        request.setEmail("refresh@example.com");
        request.setPassword("password123");
        request.setOrganizationName("Org Refresh");

        String registerBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LoginResponse registerResponse = objectMapper.readValue(registerBody, LoginResponse.class);
        String refreshToken = registerResponse.getRefreshToken();

        String refreshBody = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponse refreshResponse = objectMapper.readValue(refreshBody, LoginResponse.class);
        assertThat(refreshResponse.getAccessToken()).isNotNull().isNotEqualTo(registerResponse.getAccessToken());
        assertThat(refreshResponse.getRefreshToken()).isNotNull().isNotEqualTo(refreshToken);
    }

    @Test
    void refresh_ShouldReject_WhenInvalidToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void logout_ShouldRevokeTokens_WhenAuthenticated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Logout");
        request.setLastName("User");
        request.setEmail("logout@example.com");
        request.setPassword("password123");
        request.setOrganizationName("Org Logout");

        String registerBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LoginResponse registerResponse = objectMapper.readValue(registerBody, LoginResponse.class);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + registerResponse.getAccessToken()))
                .andExpect(status().isNoContent());

        String refreshBody = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerResponse.getRefreshToken()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
    }
}
