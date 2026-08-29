package com.mariageplus.service;

import com.mariageplus.dto.guestcategory.CreateGuestCategoryRequest;
import com.mariageplus.dto.guestcategory.GuestCategoryResponse;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.Event;
import com.mariageplus.service.EventService;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.GuestCategoryMapper;
import com.mariageplus.repository.GuestCategoryRepository;
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
class GuestCategoryServiceTest {

    @Mock private GuestCategoryRepository guestCategoryRepository;
    @Mock private GuestCategoryMapper guestCategoryMapper;
    @Mock private EventService eventService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditService auditService;

    @InjectMocks private GuestCategoryService guestCategoryService;

    private Event wedding;

    @BeforeEach
    void setUp() {
        wedding = Event.builder().organizationId(100L).build();
        wedding.setId(1L);
        lenient().when(guestCategoryMapper.toResponse(any(GuestCategory.class)))
                .thenReturn(GuestCategoryResponse.builder().build());
    }

    private CreateGuestCategoryRequest validRequest() {
        CreateGuestCategoryRequest req = new CreateGuestCategoryRequest();
        req.setName("Famille du marié");
        return req;
    }

    @Test
    void create_AssignsWeddingAndDefaultActive() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.save(any(GuestCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        guestCategoryService.create(1L, validRequest());

        ArgumentCaptor<GuestCategory> captor = ArgumentCaptor.forClass(GuestCategory.class);
        verify(guestCategoryRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getWeddingId());
        assertTrue(captor.getValue().isActive());
        assertEquals("Famille du marié", captor.getValue().getName());
    }

    @Test
    void getById_NotFound_Throws() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.findByIdAndWeddingId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> guestCategoryService.getById(1L, 99L));
    }

    @Test
    void list_FiltersByWeddingInDatabase() {
        GuestCategory category = GuestCategory.builder().weddingId(1L).build();
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.findByWeddingId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(category), PageRequest.of(0, 10), 1));

        guestCategoryService.list(1L, 0, 10, "id", "asc");

        verify(guestCategoryRepository).findByWeddingId(eq(1L), any(Pageable.class));
    }

    @Test
    void delete_SoftDeletesCategory() {
        GuestCategory category = GuestCategory.builder().weddingId(1L).build();
        category.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(category));
        when(guestCategoryRepository.save(any(GuestCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        guestCategoryService.delete(1L, 5L);

        ArgumentCaptor<GuestCategory> captor = ArgumentCaptor.forClass(GuestCategory.class);
        verify(guestCategoryRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }
}