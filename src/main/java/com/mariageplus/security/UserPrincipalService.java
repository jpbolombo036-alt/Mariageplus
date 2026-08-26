package com.mariageplus.security;

import com.mariageplus.entity.User;
import com.mariageplus.entity.OrganizationMember;
import com.mariageplus.repository.OrganizationMemberRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.UserRoleRepository;
import com.mariageplus.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Charge le principal d'un utilisateur : rôles (multi), permissions (union des
 * permissions de ses rôles) et organisation courante (membre actif).
 * Un compte désactivé lève une exception et est donc refusé.
 */
@Service
@RequiredArgsConstructor
public class UserPrincipalService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Override
    @Transactional(noRollbackFor = org.springframework.security.core.userdetails.UsernameNotFoundException.class)
    public UserDetails loadUserByUsername(String email) throws org.springframework.security.core.userdetails.UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Utilisateur non trouvé: " + email));
        return buildPrincipal(user);
    }

    @Transactional(noRollbackFor = org.springframework.security.core.userdetails.UsernameNotFoundException.class)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Utilisateur non trouvé avec l'ID: " + id));
        return buildPrincipal(user);
    }

    private UserPrincipal buildPrincipal(User user) {
        if (user == null || !user.isActive()) {
            throw new UsernameNotFoundException("Compte désactivé ou introuvable");
        }

        List<String> roleCodes = userRoleRepository.findRoleCodesByUserId(user.getId());
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());
        List<String> permissions = roleIds.isEmpty()
                ? List.of()
                : rolePermissionRepository.findCodesByRoleIds(roleIds);

        Long organizationId = organizationMemberRepository.findByUser_IdAndActiveTrue(user.getId())
                .map(m -> m.getOrganization().getId())
                .orElse(null);

        // Weddings assignés (scoping agent) : tous les mariages des membres actifs,
        // même si l'organisation "courante" n'en garde qu'une.
        List<Long> weddingIds = organizationMemberRepository.findAllByUser_IdAndActiveTrue(user.getId()).stream()
                .map(OrganizationMember::getWeddingId)
                .filter(java.util.Objects::nonNull)
                .toList();

        return UserPrincipal.create(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                roleCodes,
                permissions,
                organizationId,
                weddingIds,
                user.getTokenVersion(),
                user.isActive()
        );
    }
}
