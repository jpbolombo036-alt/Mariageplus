package com.mariageplus.service;

import com.mariageplus.dto.organization.OrganizationRequest;
import com.mariageplus.dto.organization.OrganizationResponse;
import com.mariageplus.entity.Organization;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private Organization org;

    @BeforeEach
    void setUp() {
        org = Organization.builder().name("Agence Mariage Plus").active(true).build();
        org.setId(1L);
    }

    @Test
    void getById_ShouldReturn_WhenExists() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        OrganizationResponse result = organizationService.getById(1L);
        assertNotNull(result);
        assertEquals("Agence Mariage Plus", result.getName());
    }

    @Test
    void getById_ShouldThrow_WhenNotFound() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> organizationService.getById(99L));
    }

    @Test
    void create_ShouldThrowConflict_WhenNameExists() {
        OrganizationRequest req = new OrganizationRequest();
        req.setName("Agence Mariage Plus");
        when(organizationRepository.existsByName("Agence Mariage Plus")).thenReturn(true);
        assertThrows(ConflictException.class, () -> organizationService.create(req));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void create_ShouldCreate_WhenValid() {
        OrganizationRequest req = new OrganizationRequest();
        req.setName("Nouvelle Agence");
        when(organizationRepository.existsByName("Nouvelle Agence")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(org);
        OrganizationResponse result = organizationService.create(req);
        assertNotNull(result);
        verify(organizationRepository, times(1)).save(any(Organization.class));
    }
}