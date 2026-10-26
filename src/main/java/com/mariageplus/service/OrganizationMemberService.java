package com.mariageplus.service;

import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.dto.organization.OrganizationMemberResponse;
import com.mariageplus.dto.organization.OrganizationMemberUpdateRequest;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.entity.UserRole;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ForbiddenException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.UserRoleRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestion de l'équipe : ajout de membres (utilisateurs) rattachés à une
 * organisation avec un rôle dans ce périmètre.
 */
@Service
@RequiredArgsConstructor
public class OrganizationMemberService {

    /**
     * Rôles qu'un organisateur peut attribuer dans son organisation. Le rôle
     * SUPER_ADMIN est global à la plateforme et ne peut être attribué que par
     * un SUPER_ADMIN déjà authentifié.
     */
    private static final Set<String> ORGANIZATION_ROLE_CODES = Set.of(
            "ORGANISATEUR", "GESTIONNAIRE_INVITES", "AGENT_ACCUEIL");

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationService organizationService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final EventRepository eventRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    public List<OrganizationMemberResponse> listMembers(Long organizationId) {
        return organizationMemberRepository.findByOrganization_Id(organizationId).stream()
                .map(this::buildResponse).collect(Collectors.toList());
    }

    @Transactional
    public OrganizationMemberResponse addMember(Long organizationId, OrganizationMemberRequest request) {
        Organization organization = organizationService.getOrganization(organizationId);
        ensureRoleCanBeAssigned(request.getRoleCode());
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
     * Met à jour un membre : changement de rôle et/ou de mariage assigné.
     * Au moins l'un des deux champs ({@code roleCode}, {@code weddingId}) doit être fourni.
     *
     * @param organizationId l'organisation propriétaire du membre
     * @param memberId       l'ID du membre à modifier
     * @param request        les champs à mettre à jour (roleCode et/ou weddingId)
     * @return la vue mise à jour du membre
     * @throws ResourceNotFoundException si le membre n'existe pas dans l'organisation
     * @throws IllegalArgumentException  si ni roleCode ni weddingId n'est fourni, ou si
     *                                   le rôle devient un rôle d'agent sans weddingId
     * @throws ConflictException         si le membre existe déjà avec le nouveau rôle + weddingId
     */
    @Transactional
    public OrganizationMemberResponse updateMember(Long organizationId, Long memberId,
                                                   OrganizationMemberUpdateRequest request) {
        if (request.getRoleCode() == null && request.getWeddingId() == null) {
            throw new IllegalArgumentException("Au moins un champ (roleCode ou weddingId) est requis");
        }
        if (request.getRoleCode() != null) {
            ensureRoleCanBeAssigned(request.getRoleCode());
        }

        OrganizationMember member = getMember(memberId);
        ensureMemberInOrganization(member, organizationId);

        String oldRoleCode = member.getRole().getCode();
        Long oldWeddingId = member.getWeddingId();

        // 1. Mise à jour du rôle si demandé.
        if (request.getRoleCode() != null && !request.getRoleCode().equals(oldRoleCode)) {
            Role newRole = roleRepository.findByCode(request.getRoleCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Rôle introuvable: " + request.getRoleCode()));
            boolean newRoleIsAgent = isAgentRole(newRole.getCode());

            // Détermination du weddingId : un rôle d'agent requiert un wedding (fourni ou hérité),
            // un rôle org-wide doit le vider.
            Long resolvedWeddingId;
            if (request.getWeddingId() != null) {
                resolvedWeddingId = request.getWeddingId();
            } else if (newRoleIsAgent) {
                resolvedWeddingId = oldWeddingId; // hérite du wedding existant si possible
            } else {
                resolvedWeddingId = null; // rôle org-wide : wedding vidé
            }
            if (newRoleIsAgent && resolvedWeddingId == null) {
                throw new IllegalArgumentException("Le weddingId est requis pour le rôle " + newRole.getCode());
            }
            if (resolvedWeddingId != null) {
                ensureWeddingBelongsToOrg(resolvedWeddingId, organizationId);
                if (organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                        member.getUser().getId(), organizationId, newRole.getId(), resolvedWeddingId)) {
                    throw new ConflictException("Ce membre est déjà rattaché à ce mariage avec ce rôle");
                }
            }

            User user = member.getUser();
            removeUserRole(user, oldRoleCode);
            userService.assignRole(user, newRole.getCode());
            member.setRole(newRole);
            member.setWeddingId(resolvedWeddingId);
        } else if (request.getWeddingId() != null && !request.getWeddingId().equals(oldWeddingId)) {
            // 2. Changement de wedding sans changement de rôle : valider le scoping.
            boolean agent = isAgentRole(oldRoleCode);
            if (agent && request.getWeddingId() == null) {
                throw new IllegalArgumentException("Le weddingId est requis pour le rôle " + oldRoleCode);
            }
            ensureWeddingBelongsToOrg(request.getWeddingId(), organizationId);
            // Vérifier qu'il n'y a pas de doublon (même user, même org, même rôle, même wedding).
            if (organizationMemberRepository.existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(
                    member.getUser().getId(), organizationId, member.getRole().getId(), request.getWeddingId())) {
                throw new ConflictException("Ce membre est déjà rattaché à ce mariage avec ce rôle");
            }
            member.setWeddingId(request.getWeddingId());
        }

        OrganizationMember updated = organizationMemberRepository.save(member);
        auditService.record("MEMBER_UPDATE", updated.getId(), "OrganizationMember",
                securityUtils.getCurrentUserId(), organizationId,
                "role=" + updated.getRole().getCode() + ", weddingId=" + updated.getWeddingId());
        return buildResponse(updated);
    }

    /**
     * Retire un rôle global d'un utilisateur (utile lors du changement de rôle
     * d'un membre : on ne garde que le rôle lié au nouvel OrganizationMember).
     */
    @Transactional
    public void removeUserRole(User user, String roleCode) {
        Optional<Role> roleOpt = roleRepository.findByCode(roleCode);
        if (roleOpt.isPresent()) {
            List<UserRole> userRoles = userRoleRepository.findByUser_Id(user.getId());
            for (UserRole ur : userRoles) {
                if (ur.getRole().getId().equals(roleOpt.get().getId())) {
                    userRoleRepository.delete(ur);
                }
            }
        }
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

    private void ensureRoleCanBeAssigned(String roleCode) {
        if (securityUtils.isSuperAdmin()) {
            return;
        }
        if (!ORGANIZATION_ROLE_CODES.contains(roleCode)) {
            throw new ForbiddenException("Ce rôle ne peut pas être attribué par un organisateur");
        }
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
