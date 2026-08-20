package com.mariageplus.service;

import com.mariageplus.dto.user.UserRequest;
import com.mariageplus.dto.user.UserResponse;
import com.mariageplus.entity.User;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .firstName("Jean")
                .lastName("Kabongo")
                .email("jean@example.com")
                .passwordHash("encoded")
                .active(true)
                .build();
        testUser.setId(1L);
    }

    @Test
    void getById_ShouldReturnUser_WhenExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRoleRepository.findRoleCodesByUserId(1L)).thenReturn(java.util.List.of("ORGANISATEUR"));
        when(organizationMemberRepository.findByUser_IdAndActiveTrue(1L))
                .thenReturn(Optional.empty());

        UserResponse result = userService.getById(1L);

        assertNotNull(result);
        assertEquals("jean@example.com", result.getEmail());
        assertTrue(result.getRoles().contains("ORGANISATEUR"));
        assertFalse(result.isActive() == false);
    }

    @Test
    void getById_ShouldThrow_WhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(99L));
    }

    @Test
    void create_ShouldThrowConflict_WhenEmailExists() {
        UserRequest req = new UserRequest();
        req.setEmail("jean@example.com");
        req.setFirstName("Jean");
        req.setLastName("Kabongo");
        req.setPassword("password123");

        when(userRepository.existsByEmail("jean@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> userService.create(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_ShouldCreateUser_WhenValid() {
        UserRequest req = new UserRequest();
        req.setEmail("new@example.com");
        req.setFirstName("Marie");
        req.setLastName("Mukendi");
        req.setPassword("password123");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRoleRepository.findRoleCodesByUserId(1L)).thenReturn(java.util.List.of());
        when(organizationMemberRepository.findByUser_IdAndActiveTrue(1L)).thenReturn(Optional.empty());

        UserResponse result = userService.create(req);
        assertNotNull(result);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void toggleActive_ShouldFlipActiveFlag() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRoleRepository.findRoleCodesByUserId(1L)).thenReturn(java.util.List.of());
        when(organizationMemberRepository.findByUser_IdAndActiveTrue(1L)).thenReturn(Optional.empty());

        UserResponse result = userService.toggleActive(1L);
        assertNotNull(result);
        assertFalse(result.isActive());
    }
}