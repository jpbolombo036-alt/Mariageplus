package com.mariageplus.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Principal Spring Security d'un utilisateur connecté.
 * Porte : l'identifiant, l'email (login), le hash de mot de passe, les rôles
 * (ROLE_*), les permissions granulaires et l'organisation courante (isolation
 * multi-tenant). Un SUPER_ADMIN n'est lié à aucune organisation (null).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long id;
    private String email;
    private String password;
    private List<String> roles;
    private List<String> permissions;
    private Long organizationId;
    private List<Long> weddingIds;
    private long tokenVersion;
    private boolean active;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(Long id, String email, String password,
                                        List<String> roleCodes, List<String> permissions,
                                        Long organizationId, List<Long> weddingIds,
                                        long tokenVersion, boolean active) {
        List<GrantedAuthority> authorities = roleCodes.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + code))
                .collect(Collectors.toList());
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return new UserPrincipal(id, email, password, roleCodes, permissions, organizationId, weddingIds,
                tokenVersion, active, authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
