package com.mariageplus.service;

import com.mariageplus.dto.weddingevent.CreateWeddingEventRequest;
import com.mariageplus.dto.weddingevent.WeddingEventResponse;
import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingEvent;
import com.mariageplus.entity.WeddingEventType;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.WeddingEventMapper;
import com.mariageplus.repository.WeddingEventRepository;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeddingEventServiceTest {

    @Mock private WeddingEventRepository weddingEventRepository;
    @Mock private WeddingEventMapper weddingEventMapper;
    @Mock private WeddingService weddingService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditService auditService;

    @InjectMocks private WeddingEventService weddingEventService;

    private Wedding wedding;

    @BeforeEach
    void setUp() {
        wedding = Wedding.builder().organizationId(100L).build();
        wedding.setId(1L);
        lenient().when(weddingEventMapper.toResponse(any(WeddingEvent.class)))
                .thenReturn(WeddingEventResponse.builder().build());
    }

    private CreateWeddingEventRequest validRequest() {
        CreateWeddingEventRequest req = new CreateWeddingEventRequest();
        req.setName("Réception");
        req.setType(WeddingEventType.RECEPTION);
        req.setStartTime(LocalTime.of(17, 0));
        req.setEndTime(LocalTime.of(23, 0));
        return req;
    }

    @Test
    void create_InvalidTimes_Throws() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        CreateWeddingEventRequest req = validRequest();
        req.setStartTime(LocalTime.of(14, 0));
        req.setEndTime(LocalTime.of(13, 0));

        assertThrows(IllegalArgumentException.class, () -> weddingEventService.create(1L, req));
        verify(weddingEventRepository, never()).save(any(WeddingEvent.class));
    }

    @Test
    void create_AssignsWeddingIdAndDefaultActive() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingEventRepository.save(any(WeddingEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        weddingEventService.create(1L, validRequest());

        ArgumentCaptor<WeddingEvent> captor = ArgumentCaptor.forClass(WeddingEvent.class);
        verify(weddingEventRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getWeddingId());
        assertTrue(captor.getValue().isActive());
        assertEquals(WeddingEventType.RECEPTION, captor.getValue().getType());
    }

    @Test
    void getById_NotFound_Throws() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingEventRepository.findByIdAndWeddingId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> weddingEventService.getById(1L, 99L));
    }

    @Test
    void list_FiltersByWeddingInDatabase() {
        WeddingEvent event = WeddingEvent.builder().weddingId(1L).build();
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingEventRepository.findByWeddingId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 10), 1));

        weddingEventService.list(1L, 0, 10, "id", "asc");

        verify(weddingEventRepository).findByWeddingId(eq(1L), any(Pageable.class));
    }

    @Test
    void delete_SoftDeletesEvent() {
        WeddingEvent event = WeddingEvent.builder().weddingId(1L).build();
        event.setId(5L);
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(weddingEventRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(event));
        when(weddingEventRepository.save(any(WeddingEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        weddingEventService.delete(1L, 5L);

        ArgumentCaptor<WeddingEvent> captor = ArgumentCaptor.forClass(WeddingEvent.class);
        verify(weddingEventRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }
}