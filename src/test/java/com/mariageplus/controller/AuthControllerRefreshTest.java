package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerRefreshTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        AuthService authService = mock(AuthService.class);
        when(authService.refreshToken(anyString())).thenReturn(
                new LoginResponse("access", "refresh", 900, "Bearer", null));
        controller = new AuthController(authService, new ObjectMapper());
    }

    private String refreshRawBody(String body) {
        return controller.refresh(body).getBody().getAccessToken();
    }

    @Test
    void extract_LeavesRawJwtUntouched() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2gifQ.signature";
        assertEquals(jwt, controller.extractRefreshToken(jwt));
    }

    @Test
    void extract_ReadsFromJsonWrapperWhenSentAsObject() {
        String json = "{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCJ9.sig\"}";
        assertEquals("eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCJ9.sig",
                controller.extractRefreshToken(json));
    }

    @Test
    void extract_StripsCopiedQuotesAndWhitespace() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCJ9.sig";
        assertEquals(jwt, controller.extractRefreshToken("\"" + jwt + "\""));
        assertEquals(jwt, controller.extractRefreshToken("  " + jwt + "  "));
    }

    @Test
    void refresh_ReturnsTokens_ForRawStringBody() {
        assertEquals("access", refreshRawBody("eyJhbGciOiJIUzI1NiJ9.sig"));
        assertTrue(true);
    }

    @Test
    void refresh_ReturnsTokens_ForJsonWrapperBody() {
        assertEquals("access", refreshRawBody("{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.sig\"}"));
    }
}