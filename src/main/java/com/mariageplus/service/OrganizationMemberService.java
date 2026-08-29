package com.mariageplus.service;

import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.dto.organization.OrganizationMemberResponse;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.EventRepository;
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
    private final EventRepository eventRepository;
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

        Long weddingId = request.getWeddingId();
        boolean agent = isAgentRole(role.getCode());
        if (agent && weddingId == null) {
            throw new IllegalArgumentException("Le weddingId est requis pour le rôle " + role.getCode());
        }
        if (weddingId != null) {
            ensureWeddingBelongsToOrg(weddingId, organizationId);
        }

        // Réutilise un compte existant si l'email est déjà présent (plus de 409).
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> createUser(request));
        userService.assignRole(user, request.getRoleCode());

        if (organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                user.getId(), organizationId, role.getId(), weddingId)) {
            throw new ConflictException("Ce membre est déjà rattaché à ce mariage avec ce rôle");
        }

        OrganizationMember member = OrganizationMember.builder()
                .user(user)
                .organization(organization)
                .role(role)
                .weddingId(weddingId)
                .active(true)
                .build();
        OrganizationMember savedMember = organizationMemberRepository.save(member);
        return buildResponse(savedMember);
    }

    /**
     * Supprime (soft-delete) une affectation de membre dans une organisation.
     */
    @Transactional
    public void removeMember(Long organizationId, Long memberId) {
        OrganizationMember member = getMember(memberId);
        ensureMemberInOrganization(member, organizationId);
        member.softDelete();
        organizationMemberRepository.save(member);
    }

    /**
     * Change le mariage assigné d'un membre (scoping agent). Le wedding doit
     * appartenir à l'organisation et rester unique pour (user, org, rôle).
     */
    @Transactional
    public OrganizationMemberResponse updateMemberWedding(Long organizationId, Long memberId, Long newWeddingId) {
        OrganizationMember member = getMember(memberId);
        ensureMemberInOrganization(member, organizationId);
        ensureWeddingBelongsToOrg(newWeddingId, organizationId);

        boolean agent = isAgentRole(member.getRole().getCode());
        if (agent && newWeddingId == null) {
            throw new IllegalArgumentException("Le weddingId est requis pour le rôle " + member.getRole().getCode());
        }

        boolean unchanged = (member.getWeddingId() == null && newWeddingId == null)
                || (member.getWeddingId() != null && member.getWeddingId().equals(newWeddingId));
        if (!unchanged && organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                member.getUser().getId(), organizationId, member.getRole().getId(), newWeddingId)) {
            throw new ConflictException("Ce membre est déjà rattaché à ce mariage avec ce rôle");
        }

        member.setWeddingId(newWeddingId);
        OrganizationMember updated = organizationMemberRepository.save(member);
        return buildResponse(updated);
    }

    @Transactional
    public OrganizationMemberResponse toggleActive(Long memberId) {
        OrganizationMember member = getMember(memberId);
        member.setActive(!member.isActive());
        OrganizationMember updated = organizationMemberRepository.save(member);
        return buildResponse(updated);
    }

    private User createUser(OrganizationMemberRequest request) {
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .build();
        return userRepository.save(user);
    }

    private void ensureWeddingBelongsToOrg(Long weddingId, Long organizationId) {
        Event event = eventRepository.findById(weddingId)
                .orElseThrow(() -> new ResourceNotFoundException("Mariage non trouvé avec l'ID: " + weddingId));
        if (!organizationId.equals(event.getOrganizationId())) {
            throw new IllegalArgumentException("Ce mariage n'appartient pas à cette organisation");
        }
    }

    private void ensureMemberInOrganization(OrganizationMember member, Long organizationId) {
        if (member.getOrganization() == null || !organizationId.equals(member.getOrganization().getId())) {
            throw new ResourceNotFoundException("Membre introuvable avec l'ID: " + member.getId());
        }
    }

    private boolean isAgentRole(String roleCode) {
        return "GESTIONNAIRE_INVITES".equals(roleCode) || "AGENT_ACCUEIL".equals(roleCode);
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
                .weddingId(member.getWeddingId())
                .active(member.isActive())
                .build();
    }
}