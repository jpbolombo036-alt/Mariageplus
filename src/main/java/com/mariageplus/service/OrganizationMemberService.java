package com.mariageplus.service;

import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.dto.organization.OrganizationMemberResponse;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Gestion de l'équipe : ajout de membres (utilisateurs) rattachés à une
 * organisation avec un rôle dans ce périmètre.
 */
@Service
@RequiredArgsConstructor
public class OrganizationMemberService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationService organizationService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public List<OrganizationMemberResponse> listMembers(Long organizationId) {
        return organizationMemberRepository.findByOrganization_Id(organizationId).stream()
                .map(this::buildResponse).collect(Collectors.toList());
    }

    @Transactional
    public OrganizationMemberResponse addMember(Long organizationId, OrganizationMemberRequest request) {
        Organization organization = organizationService.getOrganization(organizationId);
        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new ResourceNotFoundException("Rôle introuvable: " + request.getRoleCode()));

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email déjà utilisé");
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
        userService.assignRole(saved, request.getRoleCode());

        OrganizationMember member = OrganizationMember.builder()
                .user(saved)
                .organization(organization)
                .role(role)
                .active(true)
                .build();
        OrganizationMember savedMember = organizationMemberRepository.save(member);
        return buildResponse(savedMember);
    }

    @Transactional
    public OrganizationMemberResponse toggleActive(Long memberId) {
        OrganizationMember member = getMember(memberId);
        member.setActive(!member.isActive());
        OrganizationMember updated = organizationMemberRepository.save(member);
        return buildResponse(updated);
    }

    private OrganizationMember getMember(Long memberId) {
        return organizationMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre introuvable avec l'ID: " + memberId));
    }

    private OrganizationMemberResponse buildResponse(OrganizationMember member) {
        User user = member.getUser();
        return OrganizationMemberResponse.builder()
                .id(member.getId())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleCode(member.getRole().getCode())
                .active(member.isActive())
                .build();
    }
}