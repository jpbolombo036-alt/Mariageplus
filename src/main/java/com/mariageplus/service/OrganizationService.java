package com.mariageplus.service;

import com.mariageplus.dto.organization.OrganizationRequest;
import com.mariageplus.dto.organization.OrganizationResponse;
import com.mariageplus.entity.Organization;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public List<OrganizationResponse> getAll() {
        return organizationRepository.findAll().stream()
                .map(this::buildResponse).collect(Collectors.toList());
    }

    public OrganizationResponse getById(Long id) {
        return buildResponse(getOrganization(id));
    }

    public Organization getOrganization(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation non trouvée avec l'ID: " + id));
    }

    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new ConflictException("Nom d'organisation déjà utilisé");
        }
        Organization organization = Organization.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .active(request.getActive() == null || request.getActive())
                .build();
        Organization saved = organizationRepository.save(organization);
        return buildResponse(saved);
    }

    @Transactional
    public OrganizationResponse update(Long id, OrganizationRequest request) {
        Organization organization = getOrganization(id);
        if (request.getName() != null && !request.getName().equals(organization.getName())) {
            if (organizationRepository.existsByName(request.getName())) {
                throw new ConflictException("Nom d'organisation déjà utilisé");
            }
            organization.setName(request.getName());
        }
        if (request.getEmail() != null) organization.setEmail(request.getEmail());
        if (request.getPhone() != null) organization.setPhone(request.getPhone());
        if (request.getAddress() != null) organization.setAddress(request.getAddress());
        if (request.getActive() != null) organization.setActive(request.getActive());
        Organization updated = organizationRepository.save(organization);
        return buildResponse(updated);
    }

    @Transactional
    public OrganizationResponse toggleActive(Long id) {
        Organization organization = getOrganization(id);
        organization.setActive(!organization.isActive());
        Organization updated = organizationRepository.save(organization);
        return buildResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        Organization organization = getOrganization(id);
        organization.setActive(false);
        organization.softDelete();
        organizationRepository.save(organization);
    }

    public OrganizationResponse buildResponse(Organization organization) {
        return OrganizationResponse.builder()
                .id(organization.getId())
                .name(organization.getName())
                .email(organization.getEmail())
                .phone(organization.getPhone())
                .address(organization.getAddress())
                .active(organization.isActive())
                .createdAt(organization.getCreatedAt())
                .build();
    }
}