package com.mariageplus.service;

import com.mariageplus.dto.wedding.CreateWeddingRequest;
import com.mariageplus.dto.wedding.UpdateWeddingStatusRequest;
import com.mariageplus.dto.wedding.WeddingResponse;
import com.mariageplus.entity.Organization;
import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingStatus;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.WeddingMapper;
import com.mariageplus.repository.WeddingRepository;
import com.mariageplus.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeddingServiceTest {

    @Mock private WeddingRepository weddingRepository;
    @Mock private WeddingMapper weddingMapper;
    @Mock private SecurityUtils securityUtils;
    @Mock private OrganizationService organizationService;
    @Mock private AuditService auditService;

    @InjectMocks private WeddingService weddingService;

    private CreateWeddingRequest buildCreateRequest() {
        CreateWeddingRequest req = new CreateWeddingRequest();
        req.setGroomFirstName("Jean");
        req.setGroomLastName("Kabongo");
        req.setBrideFirstName("Marie");
        req.setBrideLastName("Mukendi");
        return req;
    }

    private Wedding buildWedding(Long id, Long orgId, WeddingStatus status) {
        Wedding w = Wedding.builder()
                .organizationId(orgId)
                .groomFirstName("Jean")
                .groomLastName("Kabongo")
                .brideFirstName("Marie")
                .brideLastName("Mukendi")
                .status(status)
                .build();
        if (id != null) w.setId(id);
        return w;
    }

    @BeforeEach
    void setUp() {
        lenient().when(weddingMapper.toResponse(any(Wedding.class))).thenReturn(WeddingResponse.builder().build());
    }

    @Test
    void create_Organizer_UsesOwnOrganization() {
        when(securityUtils.isSuperAdmin()).thenReturn(false);
        when(securityUtils.requireOrganizationId()).thenReturn(100L);
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(weddingRepository.save(any(Wedding.class))).thenAnswer(inv -> inv.getArgument(0));

        weddingService.create(buildCreateRequest());

        ArgumentCaptor<Wedding> captor = ArgumentCaptor.forClass(Wedding.class);
        verify(weddingRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getOrganizationId());
        assertEquals(WeddingStatus.DRAFT, captor.getValue().getStatus());
    }

    @Test
    void create_SuperAdmin_WithoutOrgId_Throws() {
        when(securityUtils.isSuperAdmin()).thenReturn(true);
        CreateWeddingRequest req = buildCreateRequest();
        req.setOrganizationId(null);

        assertThrows(IllegalArgumentException.class, () -> weddingService.create(req));
        verify(weddingRepository, never()).save(any(Wedding.class));
    }

    @Test
    void create_SuperAdmin_UsesProvidedOrganization() {
        when(securityUtils.isSuperAdmin()).thenReturn(true);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        Organization org = Organization.builder().name("Cible").build();
        org.setId(5L);
        when(organizationService.getOrganization(5L)).thenReturn(org);
        when(weddingRepository.save(any(Wedding.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateWeddingRequest req = buildCreateRequest();
        req.setOrganizationId(5L);
        weddingService.create(req);

        ArgumentCaptor<Wedding> captor = ArgumentCaptor.forClass(Wedding.class);
        verify(weddingRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getOrganizationId());
    }
    @Test
    void getById_NotFound_Throws() {
        when(weddingRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> weddingService.getById(99L));
    }

    @Test
    void updateStatus_InvalidTransition_Throws() {
        Wedding w = buildWedding(1L, 100L, WeddingStatus.DRAFT);
        when(weddingRepository.findById(1L)).thenReturn(Optional.of(w));

        UpdateWeddingStatusRequest req = new UpdateWeddingStatusRequest();
        req.setStatus("ARCHIVED");

        assertThrows(IllegalArgumentException.class, () -> weddingService.updateStatus(1L, req));
    }

    @Test
    void updateStatus_Publish_RequiresPublishPermission() {
        doThrow(new SecurityException("permission requise"))
                .when(securityUtils).assertPermission("WEDDING_PUBLISH");

        UpdateWeddingStatusRequest req = new UpdateWeddingStatusRequest();
        req.setStatus("PUBLISHED");

        assertThrows(SecurityException.class, () -> weddingService.updateStatus(1L, req));
    }

    @Test
    void list_NonSuperAdmin_FiltersByOrganizationInDatabase() {
        Wedding w = buildWedding(1L, 100L, WeddingStatus.DRAFT);
        when(securityUtils.isSuperAdmin()).thenReturn(false);
        when(securityUtils.requireOrganizationId()).thenReturn(100L);
        when(weddingRepository.findByOrganizationId(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(w), PageRequest.of(0, 10), 1));

        weddingService.list(0, 10, "id", "asc");

        verify(weddingRepository).findByOrganizationId(eq(100L), any(Pageable.class));
        verify(weddingRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void list_SuperAdmin_UsesFindAll() {
        Wedding w = buildWedding(1L, 100L, WeddingStatus.DRAFT);
        when(securityUtils.isSuperAdmin()).thenReturn(true);
        when(weddingRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(w), PageRequest.of(0, 10), 1));

        weddingService.list(0, 10, "id", "asc");

        verify(weddingRepository).findAll(any(Pageable.class));
        verify(weddingRepository, never()).findByOrganizationId(any(), any(Pageable.class));
    }
}