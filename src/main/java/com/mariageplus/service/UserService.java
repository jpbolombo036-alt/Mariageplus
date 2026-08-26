package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.user.UserRequest;
import com.mariageplus.dto.user.UserResponse;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.entity.UserRole;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.UserRoleRepository;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<UserResponse> getAll(int page, int size, String sortBy, String sortDir) {
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage = userRepository.findAll(pageable);
        List<UserResponse> content = userPage.getContent().stream().map(this::buildResponse).collect(Collectors.toList());
        return PageResponse.of(content, userPage);
    }

    public UserResponse getById(Long id) {
        return buildResponse(getUser(id));
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID: " + id));
    }

    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + email));
        return buildResponse(user);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email déjà utilisé");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Téléphone déjà utilisé");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ConflictException("Le mot de passe est requis");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .build();
        User saved = userRepository.save(user);

        if (request.getRoleCodes() != null) {
            for (String code : request.getRoleCodes()) {
                assignRole(saved, code);
            }
        }
        return buildResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        User user = getUser(id);
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email déjà utilisé");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new ConflictException("Téléphone déjà utilisé");
            }
            user.setPhone(request.getPhone());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        User updated = userRepository.save(user);
        return buildResponse(updated);
    }
    @Transactional
    public UserResponse toggleActive(Long id) {
        User user = getUser(id);
        user.setActive(!user.isActive());
        User updated = userRepository.save(user);
        return buildResponse(updated);
    }

    @Transactional
    public UserResponse updateProfile(Long id, UserRequest request) {
        return update(id, request);
    }

    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = getUser(id);
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit contenir au moins 8 caractères");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = getUser(id);
        user.setActive(false);
        user.softDelete();
        userRepository.save(user);
    }

    @Transactional
    public void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle introuvable: " + roleCode));
        if (!userRoleRepository.existsByUser_IdAndRole_Id(user.getId(), role.getId())) {
            userRoleRepository.save(UserRole.builder().user(user).role(role).build());
        }
    }

    public List<String> getRoleCodes(Long userId) {
        return userRoleRepository.findRoleCodesByUserId(userId);
    }

    public UserResponse buildResponse(User user) {
        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());
        List<String> permissions = roleIds.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(rolePermissionRepository.findCodesByRoleIds(roleIds));
        Long orgId = organizationMemberRepository.findByUser_IdAndActiveTrue(user.getId())
                .map(m -> m.getOrganization().getId())
                .orElse(null);
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .roles(new ArrayList<>(roles))
                .permissions(permissions)
                .organizationId(orgId)
                .build();
    }
}