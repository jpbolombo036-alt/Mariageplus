package com.mariageplus.config;

import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.entity.UserRole;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Étape 10.1 — Initialisation sécurisée du SUPER_ADMIN (contrôlée par
 * ADMIN_INIT_ENABLED). Vérifie : aucune création si désactivé ; échec contrôlé
 * sans secret si email/password absents ; création BCrypt ; jamais de doublon.
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private DataInitializer dataInitializer;

    private Role superAdmin;

    @BeforeEach
    void setUp() {
        superAdmin = new Role();
        superAdmin.setId(1L);
    }

    private void config(boolean enabled, String email, String password) {
        ReflectionTestUtils.setField(dataInitializer, "initEnabled", enabled);
        ReflectionTestUtils.setField(dataInitializer, "adminEmail", email);
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", password);
    }

    @Test
    void disabled_noSuperAdminCreated() {
        config(false, "admin@x.com", "S3cr3t!");
        dataInitializer.run();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void enabled_missingEmail_failsWithoutSecret() {
        config(true, "  ", "S3cr3t!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> dataInitializer.run());
        assertTrue(ex.getMessage().contains("ADMIN_INIT_EMAIL"));
        assertTrue(ex.getMessage().contains("ADMIN_INIT_PASSWORD"));
        assertFalse(ex.getMessage().contains("S3cr3t!")); // aucun secret exposé
    }

    @Test
    void enabled_missingPassword_failsWithoutSecret() {
        config(true, "admin@example.com", null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> dataInitializer.run());
        assertTrue(ex.getMessage().contains("ADMIN_INIT_EMAIL"));
        assertFalse(ex.getMessage().contains("Admin@12345"));
    }

    @Test
    void enabled_validCredentials_createsSuperAdmin_hashed() {
        config(true, "admin@exemple.com", "M0tDePasse!Fort");
        when(roleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByRoleId(1L)).thenReturn(false);
        when(userRepository.existsByEmail("admin@exemple.com")).thenReturn(false);
        when(passwordEncoder.encode("M0tDePasse!Fort")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(a -> a.getArgument(0));

        dataInitializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("admin@exemple.com", userCaptor.getValue().getEmail());
        // BCrypt : hash utilisé, jamais le mot de passe en clair
        assertEquals("$2a$hash", userCaptor.getValue().getPasswordHash());
        assertNotEquals("M0tDePasse!Fort", userCaptor.getValue().getPasswordHash());
        verify(passwordEncoder).encode("M0tDePasse!Fort");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(roleCaptor.capture());
        assertEquals(1L, roleCaptor.getValue().getRole().getId());
        assertEquals("admin@exemple.com", roleCaptor.getValue().getUser().getEmail());
    }

    @Test
    void enabled_superAdminAlreadyExists_noDuplicate() {
        config(true, "admin@exemple.com", "FortSecret#2026");
        when(roleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByRoleId(1L)).thenReturn(true);

        dataInitializer.run();

        verify(userRepository, never()).save(any(User.class));
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void enabled_emailAlreadyUsed_noDuplicate() {
        config(true, "admin-taken@exemple.com", "FortSecret#2026");
        when(roleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByRoleId(1L)).thenReturn(false);
        when(userRepository.existsByEmail("admin-taken@exemple.com")).thenReturn(true);

        dataInitializer.run();

        verify(userRepository, never()).save(any(User.class));
    }
}