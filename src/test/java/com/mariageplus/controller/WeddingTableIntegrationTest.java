package com.mariageplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.dto.table.WeddingTableResponse;
import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Étape 8 — Tables & Table Assignment : CRUD, capacité, affectation, déplacement,
 * retrait, isolation multi-tenant. Le test de concurrence complet (vrai parallèle)
 * est limité par H2 en mémoire : la stratégie (PESSIMISTIC_WRITE + recalcul du
 * nombre dans la transaction) est couverte par les tests unitaires
 * ({@link com.mariageplus.service.WeddingTableServiceTest}), et l'invariant de
 * capacité est vérifié ici (jamais assignedCount &gt; capacity).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeddingTableIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private WeddingTableRepository weddingTableRepository;
    @Autowired private TableAssignmentRepository tableAssignmentRepository;

    private static String tokenA;
    private static Long weddingAId;
    private static boolean initialized;
    private static int counter = 0;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            tokenA = register("tables-org@example.com", "Organisation Tables");
            weddingAId = createWedding(tokenA);
            initialized = true;
        }
    }

    private String register(String email, String orgName) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Org");
        req.setLastName("Tables");
        req.setEmail(email);
        req.setPassword("password123");
        req.setOrganizationName(orgName);
        return authService.register(req).getAccessToken();
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private Long createWedding(String token) throws Exception {
        CreateWeddingRequest req = new CreateWeddingRequest();
        req.setGroomFirstName("Jean");
        req.setGroomLastName("Kabongo");
        req.setBrideFirstName("Marie");
        req.setBrideLastName("Mukendi");
        String body = mockMvc.perform(post("/api/weddings").header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, WeddingResponse.class).getId();
    }

    private Long createGuest(Long weddingId) throws Exception {
        String name = "Invite" + (++counter);
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/guests", weddingId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + name + "\",\"lastName\":\"Guest\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GuestResponse.class).getId();
    }

    private Long createTable(Long weddingId, String name, int capacity) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"capacity\":" + capacity + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, WeddingTableResponse.class).getId();
    }

    private Long assign(Long weddingId, Long tableId, Long guestId) throws Exception {
        String body = mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guestId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("assignmentId").asLong();
    }

    private long assignedCount(Long tableId) {
        return tableAssignmentRepository.countByWeddingTableId(tableId);
    }

    // ---------- A. CRUD ----------

    @Test
    void create_valid_returnsCreatedWithCounts() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Table VIP\",\"capacity\":8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Table VIP"))
                .andExpect(jsonPath("$.capacity").value(8))
                .andExpect(jsonPath("$.assignedCount").value(0))
                .andExpect(jsonPath("$.remainingCapacity").value(8));
    }

    @Test
    void create_capacityZeroOrNegative_400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T0\",\"capacity\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T-1\",\"capacity\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankName_400() throws Exception {
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"capacity\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateName_sameWedding_409() throws Exception {
        createTable(weddingAId, "Table 1", 5);
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Table 1\",\"capacity\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_sameName_otherWedding_ok() throws Exception {
        Long weddingB = createWedding(tokenA);
        createTable(weddingAId, "Commun", 4);
        // même nom dans un autre mariage → autorisé
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingB)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Commun\",\"capacity\":4}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getById_returnsTable() throws Exception {
        Long tableId = createTable(weddingAId, "Famille marié", 6);
        mockMvc.perform(get("/api/weddings/{weddingId}/tables/{tableId}", weddingAId, tableId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Famille marié"))
                .andExpect(jsonPath("$.capacity").value(6));
    }

    @Test
    void update_ok() throws Exception {
        Long tableId = createTable(weddingAId, "Table A", 4);
        mockMvc.perform(put("/api/weddings/{weddingId}/tables/{tableId}", weddingAId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Table A2\",\"capacity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Table A2"))
                .andExpect(jsonPath("$.capacity").value(10));
    }

    @Test
    void delete_emptyTable_204() throws Exception {
        Long tableId = createTable(weddingAId, "Table vide", 4);
        mockMvc.perform(delete("/api/weddings/{weddingId}/tables/{tableId}", weddingAId, tableId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_tableWithAssignments_409() throws Exception {
        Long tableId = createTable(weddingAId, "Table occupée", 4);
        assign(weddingAId, tableId, createGuest(weddingAId));
        mockMvc.perform(delete("/api/weddings/{weddingId}/tables/{tableId}", weddingAId, tableId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isConflict());
    }
// ---------- B. Affectation ----------

    @Test
    void assign_sameWedding_ok() throws Exception {
        Long tableId = createTable(weddingAId, "B 1", 5);
        Long guest = createGuest(weddingAId);
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guest + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestId").value(guest))
                .andExpect(jsonPath("$.tableName").value("B 1"));
        assertThat(assignedCount(tableId)).isEqualTo(1);
    }

    @Test
    void assign_unknownGuest_404() throws Exception {
        Long tableId = createTable(weddingAId, "B na", 5);
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assign_guestAlreadyAssigned_409() throws Exception {
        Long table1 = createTable(weddingAId, "B g1", 5);
        Long table2 = createTable(weddingAId, "B g2", 5);
        Long guest = createGuest(weddingAId);
        assign(weddingAId, table1, guest);
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, table2)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guest + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void assign_capacityExceeded_409_and_lastSeat_ok() throws Exception {
        Long tableId = createTable(weddingAId, "B cap1", 1);
        Long g1 = createGuest(weddingAId);
        Long g2 = createGuest(weddingAId);
        assign(weddingAId, tableId, g1); // occupe la seule place
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + g2 + "}"))
                .andExpect(status().isConflict());
        assertThat(assignedCount(tableId)).isEqualTo(1);
    }

    // ---------- C. Déplacement ----------

    @Test
    void move_releasesSource_andOccupiesTarget() throws Exception {
        Long t1 = createTable(weddingAId, "C src", 5);
        Long t2 = createTable(weddingAId, "C dst", 5);
        Long guest = createGuest(weddingAId);
        Long assignmentId = assign(weddingAId, t1, guest);

        mockMvc.perform(put("/api/weddings/{weddingId}/assignments/{assignmentId}", weddingAId, assignmentId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + t2 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(t2));

        assertThat(assignedCount(t1)).isEqualTo(0);
        assertThat(assignedCount(t2)).isEqualTo(1);
        assertThat(assignedCount(t1) + assignedCount(t2)).isEqualTo(1);
    }

    @Test
    void move_targetFull_409() throws Exception {
        Long t1 = createTable(weddingAId, "C src2", 5);
        Long tFull = createTable(weddingAId, "C full", 1);
        Long gA = createGuest(weddingAId);
        Long gB = createGuest(weddingAId);
        assign(weddingAId, tFull, gA); // tFull pleine
        Long assignmentId = assign(weddingAId, t1, gB);

        mockMvc.perform(put("/api/weddings/{weddingId}/assignments/{assignmentId}", weddingAId, assignmentId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + tFull + "}"))
                .andExpect(status().isConflict());
    }
// ---------- D. Retrait ----------

    @Test
    void remove_freesCapacity() throws Exception {
        Long tableId = createTable(weddingAId, "D retr", 1);
        Long guest = createGuest(weddingAId);
        Long assignmentId = assign(weddingAId, tableId, guest);
        assertThat(assignedCount(tableId)).isEqualTo(1);

        mockMvc.perform(delete("/api/weddings/{weddingId}/assignments/{assignmentId}", weddingAId, assignmentId)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNoContent());
        assertThat(assignedCount(tableId)).isEqualTo(0);

        // capacité libérée → un autre invité peut être affecté
        Long g2 = createGuest(weddingAId);
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, tableId)
                        .header("Authorization", auth(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + g2 + "}"))
                        .andExpect(status().isCreated());
        assertThat(assignedCount(tableId)).isEqualTo(1);
    }

    @Test
    void remove_unknownAssignment_404() throws Exception {
        mockMvc.perform(delete("/api/weddings/{weddingId}/assignments/{assignmentId}", weddingAId, 999999L)
                        .header("Authorization", auth(tokenA)))
                .andExpect(status().isNotFound());
    }

    // ---------- E. Isolation multi-tenant ----------

    @Test
    void wrongOrganization_createTable_403() throws Exception {
        String otherToken = register("tables-other@example.com", "Autre Organisation");
        mockMvc.perform(post("/api/weddings/{weddingId}/tables", weddingAId)
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pirate\",\"capacity\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongOrganization_assign_403() throws Exception {
        String otherToken = register("tables-other2@example.com", "Autre Org 2");
        Long tableId = createTable(weddingAId, "E sec", 5);
        Long guest = createGuest(weddingAId);
        mockMvc.perform(post("/api/weddings/{weddingId}/tables/{tableId}/assignments", weddingAId, tableId)
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":" + guest + "}"))
                .andExpect(status().isForbidden());
    }
}