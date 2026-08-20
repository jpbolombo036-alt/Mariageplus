package com.mariageplus.config;

import com.mariageplus.entity.Role;
import com.mariageplus.repository.RolePermissionRepository;
import com.mariageplus.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GestionnaireCategoryPermissionSeedTest {

    @Autowired private RoleRepository roleRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;

    @Test
    void gestionnaireInvites_hasCategoryPermissions() {
        Role role = roleRepository.findByCode("GESTIONNAIRE_INVITES").orElseThrow();
        List<String> codes = rolePermissionRepository.findCodesByRoleIds(List.of(role.getId()));
        assertThat(codes).contains("CATEGORY_VIEW", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DELETE");
    }
}
