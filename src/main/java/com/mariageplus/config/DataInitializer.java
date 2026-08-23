package com.mariageplus.config;

import com.mariageplus.entity.Role;
import com.mariageplus.entity.User;
import com.mariageplus.entity.UserRole;
import com.mariageplus.repository.RoleRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Crée le premier compte SUPER_ADMIN au démarrage s'il n'existe pas.
 * Les rôles et permissions sont seedées par les migrations Flyway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.init-enabled:false}")
    private boolean initEnabled;

    @Value("${app.admin.init-email:}")
    private String adminEmail;

    @Value("${app.admin.init-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!initEnabled) {
            log.debug("Initialisation SUPER_ADMIN désactivée (ADMIN_INIT_ENABLED)");
            return;
        }
        if (!hasInitCredentials()) {
            // JWT_SECRET / DB OK malgré une config admin incomplète : on ne fait PAS planter tout le contexte.
            log.error("ADMIN_INIT_ENABLED=true mais ADMIN_INIT_EMAIL / ADMIN_INIT_PASSWORD absents -> "
                    + "initialisation du SUPER_ADMIN ignorée, démarrage poursuivi.");
            return;
        }
        ensureSuperAdmin();
    }

    /** True si l'email ET le mot de passe de l'init admin sont renseignés (jamais de secret dans le log). */
    private boolean hasInitCredentials() {
        return StringUtils.hasText(adminEmail) && StringUtils.hasText(adminPassword);
    }

    private void ensureSuperAdmin() {
        Optional<Role> role = roleRepository.findByCode("SUPER_ADMIN");
        if (role.isEmpty()) {
            log.warn("Rôle SUPER_ADMIN introuvable : le compte admin n'a pas été initialisé.");
            return;
        }
        // Jamais de doublon : un SUPER_ADMIN (via le rôle) existe déjà ou l'email est déjà utilisé.
        if (alreadyHasSuperAdmin(role.get().getId())) {
            return;
        }
        User user = User.builder()
                .firstName("Admin")
                .lastName("MariagePlus")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .active(true)
                .emailVerified(true)
                .build();
        User saved = userRepository.save(user);
        userRoleRepository.save(UserRole.builder().user(saved).role(role.get()).build());
        log.info("Compte SUPER_ADMIN initialisé : {}", adminEmail);
    }

    private boolean alreadyHasSuperAdmin(Long roleId) {
        return userRoleRepository.existsByRoleId(roleId) || userRepository.existsByEmail(adminEmail);
    }
}