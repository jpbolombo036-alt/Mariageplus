package com.mariageplus.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Scoping par mariage : un agent (GESTIONNAIRE_INVITES / AGENT_ACCUEIL) n'accède
 * qu'aux mariages qui lui sont assignés ; SUPER_ADMIN et ORGANISATEUR ne sont pas
 * limités par le wedding (l'org reste vérifiée par loadInOrgScope).
 */
class SecurityUtilsTest {

    private final SecurityUtils securityUtils = new SecurityUtils();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private SecurityUtils buildAgent(String role, Long... weddingIds) {
        // Authentication portée sur un principal agent.
        authenticate(UserPrincipal.create(1L, "agent@example.com", "enc",
                List.of(role), List.of(), 10L, List.of(weddingIds), 0, true));
        return securityUtils;
    }

    @Test
    void agent_AccessToAssignedWedding_isAllowed() {
        buildAgent("GESTIONNAIRE_INVITES", 42L);
        assertDoesNotThrow(() -> securityUtils.assertWeddingAccess(42L));
    }

    @Test
    void agent_WeddingOutsideScope_throws403() {
        buildAgent("AGENT_ACCUEIL", 42L);
        assertThrows(SecurityException.class, () -> securityUtils.assertWeddingAccess(43L));
    }

    @Test
    void agent_MultiWeddingAccess_IsAllowed() {
        buildAgent("GESTIONNAIRE_INVITES", 42L, 77L);
        assertDoesNotThrow(() -> securityUtils.assertWeddingAccess(77L));
    }

    @Test
    void organizer_IsNotScopedByWedding() {
        authenticate(UserPrincipal.create(1L, "org@example.com", "enc",
                List.of("ORGANISATEUR"), List.of(), 10L, List.of(), 0, true));
        // L'organisation est déjà vérifiée par loadInOrgScope : le garde wedding ne bloque pas.
        assertDoesNotThrow(() -> securityUtils.assertWeddingAccess(999L));
    }

    @Test
    void superAdmin_IsNotScopedByWedding() {
        authenticate(UserPrincipal.create(1L, "admin@example.com", "enc",
                List.of("SUPER_ADMIN"), List.of(), null, List.of(), 0, true));
        assertDoesNotThrow(() -> securityUtils.assertWeddingAccess(999L));
    }
}