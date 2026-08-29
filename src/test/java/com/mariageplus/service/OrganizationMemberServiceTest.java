package com.mariageplus.service;

import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nouvelle logique d'addMember : réutilisation d'un compte existant, weddingId
 * requis pour les agents, validation de l'appartenance du mariage, et dédoublonnage.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationMemberServiceTest {

    @Mock private OrganizationMemberRepository organizationMemberRepository;
    @Mock private OrganizationService organizationService;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRepository EventRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private OrganizationMemberService service;

    private OrganizationMemberRequest request(String role, Long weddingId, String email) {
        OrganizationMemberRequest r = new OrganizationMemberRequest();
        r.setFirstName("Agent");
        r.setLastName("Test");
        r.setEmail(email);
        r.setPassword("password123");
        r.setRoleCode(role);
        r.setWeddingId(weddingId);
        return r;
    }

    private Organization org() {
        Organization o = new Organization();
        o.setId(1L);
        return o;
    }

    private Role role(String code) {
        Role r = new Role();
        r.setId(2L);
        r.setCode(code);
        return r;
    }

    private User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    @Test
    void agentWithoutWeddingId_throwsIllegalArgument() {
        when(organizationService.getOrganization(1L)).thenReturn(org());
        when(roleRepository.findByCode("AGENT_ACCUEIL")).thenReturn(Optional.of(role("AGENT_ACCUEIL")));

        assertThrows(IllegalArgumentException.class,
                () -> service.addMember(1L, request("AGENT_ACCUEIL", null, "a@x.com")));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberWithWeddingId_reusesExistingUserAndSavesScopedMember() {
        when(organizationService.getOrganization(1L)).thenReturn(org());
        when(roleRepository.findByCode("GESTIONNAIRE_INVITES")).thenReturn(Optional.of(role("GESTIONNAIRE_INVITES")));
        Event wed = new Event();
        wed.setId(42L);
        wed.setOrganizationId(1L);
        when(EventRepository.findById(42L)).thenReturn(Optional.of(wed));

        User existing = user(5L, "agent@example.com");
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(existing));
        when(organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                5L, 1L, 2L, 42L)).thenReturn(false);
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(a -> a.getArgument(0));

        var resp = service.addMember(1L, request("GESTIONNAIRE_INVITES", 42L, "agent@example.com"));

        assertEquals(42L, resp.getWeddingId());
        verify(userRepository, never()).save(any(User.class)); // compte existant réutilisé
        verify(userService).assignRole(existing, "GESTIONNAIRE_INVITES");
    }

    @Test
    void duplicateMembership_throwsConflict() {
        when(organizationService.getOrganization(1L)).thenReturn(org());
        when(roleRepository.findByCode("GESTIONNAIRE_INVITES")).thenReturn(Optional.of(role("GESTIONNAIRE_INVITES")));
        Event wed = new Event();
        wed.setId(42L);
        wed.setOrganizationId(1L);
        when(EventRepository.findById(42L)).thenReturn(Optional.of(wed));

        Long freshUserId = 9L;
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.of(user(freshUserId, "new@example.com")));
        when(organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                freshUserId, 1L, 2L, 42L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.addMember(1L, request("GESTIONNAIRE_INVITES", 42L, "new@example.com")));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addOrganizer_ignoresWeddingWeddingIdIsNull() {
        when(organizationService.getOrganization(1L)).thenReturn(org());
        when(roleRepository.findByCode("ORGANISATEUR")).thenReturn(Optional.of(role("ORGANISATEUR")));
        when(userRepository.findByEmail("org@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(a -> a.getArgument(0));
        when(organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                any(), any(), any(), any())).thenReturn(false);
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(a -> a.getArgument(0));

        var resp = service.addMember(1L, request("ORGANISATEUR", null, "org@example.com"));

        assertNull(resp.getWeddingId()); // rôle org-wide, wedding ignoré
    }
}