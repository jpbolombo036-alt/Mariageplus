package com.mariageplus.service;

import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.service.EventService;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mariageplus.entity.AuditLog;
import com.mariageplus.repository.AuditLogRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du dashboard : calculs des agrégats, taux (sans division par
 * zéro, bornés 0..100), répartition par catégorie, et permission requise.
 */
@ExtendWith(MockitoExtension.class)
class WeddingDashboardServiceTest {

    @Mock private GuestRepository guestRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private RsvpRepository rsvpRepository;
    @Mock private CheckInRepository checkInRepository;
    @Mock private WeddingTableRepository weddingTableRepository;
    @Mock private TableAssignmentRepository tableAssignmentRepository;
    @Mock private GuestCategoryRepository guestCategoryRepository;
    @Mock private EventService eventService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks private WeddingDashboardService dashboardService;

    private Event wedding;

    @BeforeEach
    void setUp() {
        wedding = Event.builder().name("Jean Kabongo & Marie Mukendi")
                .build();
        wedding.setId(1L);
        wedding.setOrganizationId(100L);
        lenient().when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        lenient().when(guestCategoryRepository.findByWeddingId(1L)).thenReturn(List.of());
    }

    @Test
    void dashboard_empty_returnsZero() {
        weddingDashboardEmpty();
        WeddingDashboardResponse r = dashboardService.getDashboard(1L);
        assertEquals(0, r.getGuests().getTotal());
        assertEquals(0, r.getInvitations().getTotal());
        assertEquals(0, r.getInvitations().getResponseRate());
        assertEquals(0, r.getAttendance().getCheckInRate());
        assertEquals(0, r.getTables().getTotal());
        assertEquals(0, r.getGuests().getUnassigned());
    }

    @Test
    void dashboard_fullScenario_matchesExpected() {
        when(guestRepository.countByWeddingId(1L)).thenReturn(100L);
        when(invitationRepository.countByWeddingId(1L)).thenReturn(100L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.ACCEPTED)).thenReturn(60L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.DECLINED)).thenReturn(20L);
        when(rsvpRepository.sumAcceptedAttendeesByWedding(1L)).thenReturn(90);
        when(checkInRepository.sumCheckedInByWedding(1L)).thenReturn(70);
        when(weddingTableRepository.countByWeddingId(1L)).thenReturn(12L);
        when(weddingTableRepository.sumCapacityByWeddingId(1L)).thenReturn(120);
        when(tableAssignmentRepository.countByWeddingId(1L)).thenReturn(75L);

        WeddingDashboardResponse r = dashboardService.getDashboard(1L);

        assertEquals(100, r.getGuests().getTotal());
        assertEquals(25, r.getGuests().getUnassigned());
        assertEquals(60, r.getInvitations().getAccepted());
        assertEquals(20, r.getInvitations().getDeclined());
        assertEquals(20, r.getInvitations().getPending());
        assertEquals(80.0, r.getInvitations().getResponseRate(), 0.001);
        assertEquals(90, r.getAttendance().getExpected());
        assertEquals(70, r.getAttendance().getCheckedIn());
        assertEquals(20, r.getAttendance().getRemaining());
        assertEquals(77.78, r.getAttendance().getCheckInRate(), 0.01);
        assertEquals(12, r.getTables().getTotal());
        assertEquals(120, r.getTables().getCapacity());
        assertEquals(75, r.getTables().getAssignedGuests());
        assertEquals(45, r.getTables().getRemainingCapacity());
    }

    @Test
    void dashboard_zeroExpected_noDivisionByZero() {
        when(guestRepository.countByWeddingId(1L)).thenReturn(3L);
        when(invitationRepository.countByWeddingId(1L)).thenReturn(2L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.ACCEPTED)).thenReturn(0L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.DECLINED)).thenReturn(0L);
        when(rsvpRepository.sumAcceptedAttendeesByWedding(1L)).thenReturn(0);
        when(checkInRepository.sumCheckedInByWedding(1L)).thenReturn(0);
        when(weddingTableRepository.countByWeddingId(1L)).thenReturn(0L);
        when(weddingTableRepository.sumCapacityByWeddingId(1L)).thenReturn(0);
        when(tableAssignmentRepository.countByWeddingId(1L)).thenReturn(0L);

        WeddingDashboardResponse r = dashboardService.getDashboard(1L);
        assertEquals(0, r.getInvitations().getResponseRate());
        assertEquals(0, r.getAttendance().getCheckInRate());
    }

    @Test
    void dashboard_categoryBreakdown() {
        GuestCategory cat = GuestCategory.builder().name("Famille").build();
        cat.setId(7L);
        when(guestCategoryRepository.findByWeddingId(1L)).thenReturn(List.of(cat));
        when(guestRepository.countByWeddingIdAndCategoryId(1L, 7L)).thenReturn(10L);
        when(rsvpRepository.countByStatusForCategory(1L, 7L, RsvpStatus.ACCEPTED)).thenReturn(6L);
        when(rsvpRepository.countByStatusForCategory(1L, 7L, RsvpStatus.DECLINED)).thenReturn(2L);
        when(invitationRepository.countByWeddingIdAndCategory(1L, 7L)).thenReturn(10L);
        when(rsvpRepository.sumAcceptedAttendeesByCategory(1L, 7L)).thenReturn(8);
        when(guestRepository.countByWeddingId(1L)).thenReturn(10L);
        when(invitationRepository.countByWeddingId(1L)).thenReturn(10L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.ACCEPTED)).thenReturn(6L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.DECLINED)).thenReturn(2L);
        when(rsvpRepository.sumAcceptedAttendeesByWedding(1L)).thenReturn(8);
        when(checkInRepository.sumCheckedInByWedding(1L)).thenReturn(0);
        when(weddingTableRepository.countByWeddingId(1L)).thenReturn(0L);
        when(weddingTableRepository.sumCapacityByWeddingId(1L)).thenReturn(0);
        when(tableAssignmentRepository.countByWeddingId(1L)).thenReturn(0L);

        WeddingDashboardResponse r = dashboardService.getDashboard(1L);
        assertEquals(1, r.getCategories().size());
        assertEquals(10L, r.getCategories().get(0).getTotalGuests());
        assertEquals(6L, r.getCategories().get(0).getAccepted());
        assertEquals(2L, r.getCategories().get(0).getDeclined());
        assertEquals(2L, r.getCategories().get(0).getPending());
        assertEquals(8L, r.getCategories().get(0).getExpectedAttendees());
    }

    @Test
    void dashboard_countsSentAndOpenedInvitations() {
        weddingDashboardEmpty();
        when(invitationRepository.countByWeddingIdAndSentAtIsNotNull(1L)).thenReturn(228L);
        when(invitationRepository.countByWeddingIdAndOpenedAtIsNotNull(1L)).thenReturn(196L);

        WeddingDashboardResponse r = dashboardService.getDashboard(1L);
        assertEquals(228, r.getInvitations().getSent());
        assertEquals(196, r.getInvitations().getOpened());
    }

    @Test
    void recentActivity_leavesOutTechnicalTraces() {
        AuditLog guest = AuditLog.builder().action("GUEST_CREATE").details("Invité ajouté").performedAt(LocalDateTime.now()).build();
        guest.setId(5L);
        when(auditLogRepository.findByOrganizationIdAndActionNotIn(eq(100L), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(guest));

        var items = dashboardService.getRecentActivity(1L, 5);

        assertEquals(List.of("GUEST_CREATE"), items.stream().map(i -> i.getAction()).toList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(auditLogRepository).findByOrganizationIdAndActionNotIn(eq(100L), excluded.capture(), any(Pageable.class));
        assertTrue(excluded.getValue().containsAll(List.of("LOGIN_SUCCESS", "LOGOUT", "INVITATION_QR_ROTATE")));
        assertFalse(excluded.getValue().contains("GUEST_CREATE"));
        verify(auditLogRepository, never()).findByOrganizationId(any(), any());
    }

    @Test
    void dashboard_requiresPermission() {
        doThrow(new SecurityException("denied")).when(securityUtils).assertPermission("DASHBOARD_VIEW");
        assertThrows(SecurityException.class, () -> dashboardService.getDashboard(1L));
    }

    private void weddingDashboardEmpty() {
        when(guestRepository.countByWeddingId(1L)).thenReturn(0L);
        when(invitationRepository.countByWeddingId(1L)).thenReturn(0L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.ACCEPTED)).thenReturn(0L);
        when(rsvpRepository.countByStatusForWedding(1L, RsvpStatus.DECLINED)).thenReturn(0L);
        when(rsvpRepository.sumAcceptedAttendeesByWedding(1L)).thenReturn(0);
        when(checkInRepository.sumCheckedInByWedding(1L)).thenReturn(0);
        when(weddingTableRepository.countByWeddingId(1L)).thenReturn(0L);
        when(weddingTableRepository.sumCapacityByWeddingId(1L)).thenReturn(0);
        when(tableAssignmentRepository.countByWeddingId(1L)).thenReturn(0L);
    }
}