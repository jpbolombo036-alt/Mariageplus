package com.mariageplus.service;

import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.user.UserResponse;
import com.mariageplus.entity.RefreshToken;
import com.mariageplus.entity.User;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.OrganizationRepository;
import com.mariageplus.repository.RefreshTokenRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.security.JwtTokenProvider;
import com.mariageplus.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository organizationMemberRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserService userService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutDurationMinutes", 15L);
        user = User.builder()
                .email("organisateur@example.com")
                .passwordHash("encoded")
                .active(true)
                .build();
        user.setId(1L);
        principal = UserPrincipal.create(1L, "organisateur@example.com", "encoded",
                java.util.List.of("ORGANISATEUR"), java.util.List.of("WEDDING_VIEW"), null,
                java.util.List.of(), 0, true);
    }

    @Test
    void login_ShouldReturnToken_WhenCredentialsValid() {
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findWithLockByEmail("organisateur@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(principal)).thenReturn("jwt-token");
        when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(86400L);
        when(jwtTokenProvider.getRefreshExpirationSeconds()).thenReturn(604800L);
        when(userService.buildResponse(user)).thenReturn(UserResponse.builder()
                .id(1L).email("organisateur@example.com").roles(java.util.List.of("ORGANISATEUR")).build());

        LoginRequest login = new LoginRequest();
        login.setEmail("organisateur@example.com");
        login.setPassword("secret123");
        LoginResponse response = authService.login(login);

        assertNotNull(response);
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("organisateur@example.com", response.getUser().getEmail());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void login_ShouldCountFailedAttempts_AndLockAtConfiguredThreshold() {
        when(userRepository.findWithLockByEmail("organisateur@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        LoginRequest login = new LoginRequest();
        login.setEmail("organisateur@example.com");
        login.setPassword("wrong-password");

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> authService.login(login));
        }

        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        verify(userRepository, times(5)).save(user);
    }

    @Test
    void refreshToken_ShouldReturnNewTokens_WhenValid() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(1L);
        refreshToken.setToken("valid-refresh");
        refreshToken.setUserId(1L);
        refreshToken.setOrganizationId(null);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.validateToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-refresh")).thenReturn(1L);
        when(jwtTokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("new-jwt");
        when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("new-refresh");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(86400L);
        when(jwtTokenProvider.getRefreshExpirationSeconds()).thenReturn(604800L);
        when(userService.buildResponse(user)).thenReturn(UserResponse.builder().id(1L).email("organisateur@example.com").build());

        LoginResponse response = authService.refreshToken("valid-refresh");

        assertNotNull(response);
        assertEquals("new-jwt", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void logout_ShouldRevokeRefreshTokens() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.logout(1L);

        verify(refreshTokenRepository).deleteByUserIdAndDeletedAtIsNull(1L);
        assertEquals(1L, user.getTokenVersion());
        verify(userRepository).save(user);
    }

    @Test
    void me_ShouldDelegateToUserService() {
        when(userService.getById(1L)).thenReturn(UserResponse.builder().id(1L).build());
        UserResponse response = authService.me(1L);
        assertNotNull(response);
        assertEquals(1L, response.getId());
    }
}
