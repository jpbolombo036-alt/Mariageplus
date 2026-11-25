package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.entity.EventType;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private EventRepository eventRepository;

    @Test
    void superAdminSetsEveryLevelAndOrganizersOnlyTheirOwn() throws Exception {
        LoginResponse admin = login("admin@test.mariageplus.app", "Admin@12345");
        LoginResponse alice = register("reglage-alice@example.com", "Organisation Réglage Alice");
        LoginResponse bruno = register("reglage-bruno@example.com", "Organisation Réglage Bruno");
        Long aliceOrg = alice.getUser().getOrganizationId();
        Long aliceEvent = eventRepository.save(Event.builder().organizationId(aliceOrg).name("Mariage")
                .type(EventType.WEDDING).eventDate(LocalDate.of(2031, 1, 1)).status(EventStatus.PUBLISHED).build()).getId();

        // Global: SUPER_ADMIN only; inherited values are the server defaults.
        call(get("/api/admin/reminder-settings"), bruno, null).andExpect(status().isForbidden());
        call(get("/api/admin/reminder-settings"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.inherited.firstReminderDays").value(7))
                .andExpect(jsonPath("$.inherited.timeZone").value("Africa/Kinshasa"))
                .andExpect(jsonPath("$.timeZoneEditable").value(true));
        call(put("/api/admin/reminder-settings"), admin, body(null, 10, 9, "Africa/Lubumbashi")).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.firstReminderDays").value(10));

        // Organization: its organizers and the SUPER_ADMIN, not another organization.
        call(get("/api/organizations/{id}/reminder-settings", aliceOrg), bruno, null).andExpect(status().isForbidden());
        call(get("/api/organizations/{id}/reminder-settings", aliceOrg), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.sendHour", nullValue()))
                .andExpect(jsonPath("$.inherited.sendHour").value(9))
                .andExpect(jsonPath("$.inherited.timeZone").value("Africa/Lubumbashi"));
        call(put("/api/organizations/{id}/reminder-settings", aliceOrg), alice, body(false, null, 18, null))
                .andExpect(status().isOk());
        call(put("/api/organizations/{id}/reminder-settings", aliceOrg), admin, body(false, null, 19, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.sendHour").value(19));

        // Event: inherits from its organization; no time zone at this level.
        call(get("/api/events/{id}/reminder-settings", aliceEvent), bruno, null).andExpect(status().isForbidden());
        call(get("/api/events/{id}/reminder-settings", aliceEvent), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.inherited.enabled").value(false))
                .andExpect(jsonPath("$.inherited.sendHour").value(19))
                .andExpect(jsonPath("$.timeZoneEditable").value(false));
        call(put("/api/events/{id}/reminder-settings", aliceEvent), alice, body(true, null, null, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.enabled").value(true));
        call(put("/api/events/{id}/reminder-settings", aliceEvent), alice, body(true, null, null, "Africa/Kinshasa"))
                .andExpect(status().isBadRequest());

        // Validation.
        call(put("/api/organizations/{id}/reminder-settings", aliceOrg), alice, body(null, 1, null, null)).andExpect(status().isBadRequest());
        call(put("/api/organizations/{id}/reminder-settings", aliceOrg), alice, body(null, null, 24, null)).andExpect(status().isBadRequest());
        call(put("/api/organizations/{id}/reminder-settings", aliceOrg), alice, body(null, null, null, "Mars/Olympus")).andExpect(status().isBadRequest());
        call(get("/api/events/{id}/reminder-settings", 999_999L), admin, null).andExpect(status().isNotFound());
    }

    private static Map<String, Object> body(Boolean enabled, Integer firstDays, Integer sendHour, String zone) {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", enabled);
        m.put("firstReminderEnabled", null);
        m.put("firstReminderDays", firstDays);
        m.put("eveEnabled", null);
        m.put("dayOfEnabled", null);
        m.put("sendHour", sendHour);
        m.put("timeZone", zone);
        return m;
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               LoginResponse who, Map<String, Object> body) throws Exception {
        request.header("Authorization", "Bearer " + who.getAccessToken());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }

    private LoginResponse register(String email, String organizationName) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Organisateur");
        request.setLastName("Réglage");
        request.setEmail(email);
        request.setPassword("MotDePasse123");
        request.setOrganizationName(organizationName);
        return authService.register(request);
    }

    private LoginResponse login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return authService.login(request);
    }
}
