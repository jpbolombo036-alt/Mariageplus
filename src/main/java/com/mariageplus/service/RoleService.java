package com.mariageplus.service;

import com.mariageplus.dto.role.RoleRequest;
import com.mariageplus.dto.role.RoleResponse;
import com.mariageplus.entity.Permission;
import com.mariageplus.entity.Role;
import com.mariageplus.entity.RolePermission;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.PermissionRepository;
import com.mariageplus.repository.RolePermissionRepository;
import com.mariageplus.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public List<RoleResponse> getAll() {
        return roleRepository.findAll().stream().map(this::buildResponse).collect(Collectors.toList());
    }

    public RoleResponse getById(Long id) {
        return buildResponse(getRole(id));
    }

    public Role getRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle non trouvé avec l'ID: " + id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Code de rôle déjà utilisé: " + request.getCode());
        }
        Role role = Role.builder()
                .code(request.getCode())
                .description(request.getDescription())
                .active(request.getActive() == null || request.getActive())
                .build();
        Role saved = roleRepository.save(role);
        if (request.getPermissionCodes() != null) {
            replacePermissions(saved, request.getPermissionCodes());
        }
        return buildResponse(saved);
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = getRole(id);
        if (request.getCode() != null && !request.getCode().equals(role.getCode())) {
            if (roleRepository.existsByCode(request.getCode())) {
                throw new ConflictException("Code de rôle déjà utilisé: " + request.getCode());
            }
            role.setCode(request.getCode());
        }
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        if (request.getActive() != null) role.setActive(request.getActive());
        Role updated = roleRepository.save(role);
        if (request.getPermissionCodes() != null) {
            replacePermissions(updated, request.getPermissionCodes());
        }
        return buildResponse(updated);
    }

    @Transactional
    public RoleResponse replacePermissions(Long roleId, List<String> permissionCodes) {
        Role role = getRole(roleId);
        replacePermissions(role, permissionCodes);
        return buildResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        Role role = getRole(id);
        rolePermissionRepository.deleteByRole_Id(id);
        role.softDelete();
        roleRepository.save(role);
    }

    private void replacePermissions(Role role, List<String> permissionCodes) {
        rolePermissionRepository.deleteByRole_Id(role.getId());
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission introuvable: " + code));
            rolePermissionRepository.save(RolePermission.builder().role(role).permission(permission).build());
        }
    }

    public RoleResponse buildResponse(Role role) {
        List<String> permissionCodes = rolePermissionRepository.findByRole_Id(role.getId()).stream()
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toList());
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .description(role.getDescription())
                .active(role.isActive())
                .permissionCodes(permissionCodes)
                .build();
    }
}