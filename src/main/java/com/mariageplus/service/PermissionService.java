package com.mariageplus.service;

import com.mariageplus.dto.permission.PermissionResponse;
import com.mariageplus.entity.Permission;
import com.mariageplus.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public List<PermissionResponse> getAll() {
        return permissionRepository.findAllByOrderByCategorieAscCodeAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PermissionResponse toResponse(Permission p) {
        return PermissionResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .libelle(p.getLibelle())
                .categorie(p.getCategorie())
                .build();
    }
}