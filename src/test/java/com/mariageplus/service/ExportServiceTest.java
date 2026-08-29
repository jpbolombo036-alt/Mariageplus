package com.mariageplus.service;

import com.mariageplus.dto.dashboard.AttendanceStatisticsResponse;
import com.mariageplus.dto.dashboard.CategoryStatisticsResponse;
import com.mariageplus.dto.dashboard.GuestStatisticsResponse;
import com.mariageplus.dto.dashboard.InvitationStatisticsResponse;
import com.mariageplus.dto.dashboard.TableStatisticsResponse;
import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.service.EventService;
import com.mariageplus.entity.WeddingTable;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.WeddingTableRepository;
import com.mariageplus.security.SecurityUtils;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private EventService eventService;
    @Mock private GuestRepository guestRepository;
    @Mock private GuestCategoryRepository guestCategoryRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private RsvpRepository rsvpRepository;
    @Mock private CheckInRepository checkInRepository;
    @Mock private WeddingTableRepository weddingTableRepository;
    @Mock private TableAssignmentRepository tableAssignmentRepository;
    @Mock private WeddingDashboardService weddingDashboardService;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks private ExportService exportService;

    private Event wedding;

    @BeforeEach
    void setUp() {
        wedding = new Event();
        wedding.setId(1L);
        wedding.setOrganizationId(10L);
    }

    @Test
    void exportGuestsCsv_returnsCsvWithHeaders() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.findByWeddingId(1L)).thenReturn(List.of());
        Guest guest = new Guest();
        guest.setId(1L);
        guest.setWeddingId(1L);
        guest.setFirstName("Jean");
        guest.setLastName("Kabongo");
        guest.setEmail("jean@ex.com");
        guest.setPhone("+243");
        guest.setAddress("Gombe");
        guest.setAllowedCompanions(1);
        guest.setActive(true);
        when(guestRepository.findByWeddingId(1L)).thenReturn(List.of(guest));

        byte[] bytes = exportService.exportGuestsCsv(1L);

        String csv = new String(bytes);
        assertTrue(csv.startsWith("id,firstName,lastName"));
        assertTrue(csv.contains("Jean"));
        assertTrue(csv.contains("Kabongo"));
    }

    @Test
    void exportGuestsCsv_escapesSpecialCharacters() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.findByWeddingId(1L)).thenReturn(List.of());
        Guest guest = new Guest();
        guest.setId(1L);
        guest.setWeddingId(1L);
        guest.setFirstName("Jean");
        guest.setLastName("Dupont, Jr.");
        guest.setEmail("jean@ex.com");
        guest.setPhone("+243");
        guest.setAddress("Gombe");
        guest.setAllowedCompanions(0);
        guest.setActive(true);
        when(guestRepository.findByWeddingId(1L)).thenReturn(List.of(guest));

        byte[] bytes = exportService.exportGuestsCsv(1L);
        String csv = new String(bytes);
        assertTrue(csv.contains("\"Dupont, Jr.\""));
    }

    @Test
    void exportInvitationsCsv_includesRsvpStatus() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        Invitation invitation = new Invitation();
        invitation.setId(10L);
        invitation.setWeddingId(1L);
        invitation.setGuestId(5L);
        invitation.setInvitationCode("INV-1");
        invitation.setStatus(com.mariageplus.entity.InvitationStatus.SENT);
        invitation.setReminderCount(1);
        when(invitationRepository.findByWeddingId(1L)).thenReturn(List.of(invitation));
        when(rsvpRepository.findByInvitationIdIn(any())).thenReturn(List.of());

        byte[] bytes = exportService.exportInvitationsCsv(1L);
        String csv = new String(bytes);
        assertTrue(csv.contains("INV-1"));
        assertTrue(csv.contains("guestId,invitationCode"));
    }

    @Test
    void exportRsvpsCsv_listsResponses() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        Invitation invitation = new Invitation();
        invitation.setId(10L);
        invitation.setWeddingId(1L);
        invitation.setGuestId(5L);
        Rsvp rsvp = new Rsvp();
        rsvp.setId(20L);
        rsvp.setInvitationId(10L);
        rsvp.setStatus(RsvpStatus.ACCEPTED);
        rsvp.setNumberOfAttendees(2);
        when(rsvpRepository.findActiveByWeddingId(1L)).thenReturn(List.of(rsvp));
        when(invitationRepository.findByWeddingId(1L)).thenReturn(List.of(invitation));

        byte[] bytes = exportService.exportRsvpsCsv(1L);
        String csv = new String(bytes);
        assertTrue(csv.contains("ACCEPTED"));
        assertTrue(csv.contains("numberOfAttendees"));
    }

    @Test
    void exportTablesCsv_countsAssignments() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        WeddingTable table = new WeddingTable();
        table.setId(1L);
        table.setWeddingId(1L);
        table.setName("Table 1");
        table.setCapacity(10);
        when(weddingTableRepository.findByWeddingId(1L)).thenReturn(List.of(table));
        when(tableAssignmentRepository.countByWeddingTableId(1L)).thenReturn(4L);

        byte[] bytes = exportService.exportTablesCsv(1L);
        String csv = new String(bytes);
        assertTrue(csv.contains("Table 1"));
        assertTrue(csv.contains("10"));
        assertTrue(csv.contains("4"));
    }

    @Test
    void exportDashboardPdf_generatesNonEmptyPdf() {
        when(weddingDashboardService.getDashboard(1L)).thenReturn(WeddingDashboardResponse.builder()
                .weddingId(1L).weddingName("Jean & Marie")
                .guests(GuestStatisticsResponse.builder().total(10).unassigned(2).build())
                .invitations(InvitationStatisticsResponse.builder().total(8).accepted(5).declined(1).pending(2).build())
                .attendance(AttendanceStatisticsResponse.builder().expected(7).checkedIn(3).checkInRate(42.86).build())
                .tables(TableStatisticsResponse.builder().total(3).capacity(30).assignedGuests(8).remainingCapacity(22).build())
                .categories(List.of(CategoryStatisticsResponse.builder().categoryId(1L).name("VIP")
                        .totalGuests(5).accepted(3).expectedAttendees(4).build()))
                .build());

        byte[] bytes = exportService.exportDashboardPdf(1L);
        assertTrue(bytes.length > 0);
        assertTrue(bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46);
    }

    @Test
    void exportDashboardPdf_requiresReportViewPermission() {
        doThrow(new SecurityException("denied")).when(securityUtils).assertPermission("REPORT_VIEW");

        assertThrows(SecurityException.class, () -> exportService.exportDashboardPdf(1L));
        verify(eventService, never()).loadInOrgScope(any());
    }

    @Test
    void exportGuestsCsv_requiresGuestExportPermission() {
        doThrow(new SecurityException("denied")).when(securityUtils).assertPermission("GUEST_EXPORT");

        assertThrows(SecurityException.class, () -> exportService.exportGuestsCsv(1L));
        verify(guestRepository, never()).findByWeddingId(any());
    }
}
