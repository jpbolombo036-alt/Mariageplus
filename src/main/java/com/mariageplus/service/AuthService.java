package com.mariageplus.service;

import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.dto.user.UserResponse;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.RefreshToken;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.OrganizationRepository;
import com.mariageplus.repository.RefreshTokenRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.security.UserPrincipal;
import com.mariageplus.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @org.springframework.beans.factory.annotation.Value("${app.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @org.springframework.beans.factory.annotation.Value("${app.security.lockout-duration-minutes:15}")
    private long lockoutDurationMinutes;

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findWithLockByEmail(request.getEmail()).orElse(null);
        if (isTemporarilyLocked(user)) {
            // Same response as invalid credentials: do not expose account state.
            throw new BadCredentialsException("Email ou mot de passe incorrect");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            recordFailedLogin(user);
            throw new BadCredentialsException("Email ou mot de passe incorrect");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        user = userRepository.findWithLockByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        resetLoginFailures(user);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), principal.getOrganizationId());
        saveRefreshToken(user, principal.getOrganizationId(), refreshToken);

        return new LoginResponse(accessToken, refreshToken, jwtTokenProvider.getExpirationSeconds(), "Bearer", userService.buildResponse(user));
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email déjà utilisé");
        }
        Role organizerRole = roleRepository.findByCode("ORGANISATEUR")
                .orElseThrow(() -> new ResourceNotFoundException("Rôle ORGANISATEUR introuvable"));

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .build();
        User saved = userRepository.save(user);
        userService.assignRole(saved, "ORGANISATEUR");

        Organization organization = Organization.builder()
                .name(request.getOrganizationName())
                .email(request.getOrganizationEmail())
                .phone(request.getOrganizationPhone())
                .address(request.getOrganizationAddress())
                .active(true)
                .build();
        Organization savedOrg = organizationRepository.save(organization);

        organizationMemberRepository.save(OrganizationMember.builder()
                .user(saved)
                .organization(savedOrg)
                .role(organizerRole)
                .active(true)
                .build());

        UserPrincipal principal = UserPrincipal.create(
                saved.getId(), saved.getEmail(), saved.getPasswordHash(),
                java.util.List.of("ORGANISATEUR"), java.util.List.of(),
                savedOrg.getId(), java.util.List.of(), saved.getTokenVersion(), saved.isActive());

        String accessToken = jwtTokenProvider.generateToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(saved.getId(), savedOrg.getId());
        saveRefreshToken(saved, savedOrg.getId(), refreshToken);

        return new LoginResponse(accessToken, refreshToken, jwtTokenProvider.getExpirationSeconds(), "Bearer", userService.buildResponse(saved));
    }

    @Transactional
    public LoginResponse refreshToken(String token) {
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isRefreshToken(token)) {
            throw new ResourceNotFoundException("Refresh token invalide");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        RefreshToken refreshTokenEntity = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token introuvable"));

        if (refreshTokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenEntity.softDelete();
            refreshTokenRepository.save(refreshTokenEntity);
            throw new ResourceNotFoundException("Refresh token expiré");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        if (!user.isActive()) {
            throw new ResourceNotFoundException("Compte désactivé");
        }

        refreshTokenEntity.softDelete();
        refreshTokenRepository.save(refreshTokenEntity);

        Long organizationId = organizationMemberRepository.findByUser_IdAndActiveTrue(user.getId())
                .map(m -> m.getOrganization().getId())
                .orElse(null);

        UserPrincipal principal = UserPrincipal.create(
                user.getId(), user.getEmail(), user.getPasswordHash(),
                java.util.List.of(), java.util.List.of(),
                organizationId, java.util.List.of(), user.getTokenVersion(), user.isActive());

        String accessToken = jwtTokenProvider.generateToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), organizationId);
        saveRefreshToken(user, organizationId, newRefreshToken);

        return new LoginResponse(accessToken, newRefreshToken, jwtTokenProvider.getExpirationSeconds(), "Bearer", userService.buildResponse(user));
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserIdAndDeletedAtIsNull(userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });
    }

    private void saveRefreshToken(User user, Long organizationId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .userId(user.getId())
                .organizationId(organizationId)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshExpirationSeconds()))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    public UserResponse me(Long userId) {
        return userService.getById(userId);
    }

    private boolean isTemporarilyLocked(User user) {
        return user != null && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void recordFailedLogin(User user) {
        // Unknown email addresses receive the same response but are not persisted.
        if (user == null) {
            return;
        }
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxLoginAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
        }
        userRepository.save(user);
    }

    private void resetLoginFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }
}
