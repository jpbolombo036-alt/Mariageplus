package com.mariageplus.service;

import com.mariageplus.dto.checkin.CheckInRequest;
import com.mariageplus.dto.checkin.CheckInResponse;
import com.mariageplus.dto.checkin.CheckInScanResponse;
import com.mariageplus.dto.checkin.ScanCheckInRequest;
import com.mariageplus.entity.CheckIn;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.service.EventService;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.CheckInRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.TableAssignmentRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du check-in : permissions, scan, check-in, refus métier et
 * logique de verrouillage/anti-dépassement (recalcul de la somme via
 * {@link InvitationRepository#findByPublicTokenForUpdate}).
 */
@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock private CheckInRepository checkInRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private EventRepository eventRepository;
    @Mock private RsvpRepository rsvpRepository;
    @Mock private TableAssignmentRepository tableAssignmentRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditService auditService;

    @InjectMocks private CheckInService checkInService;

    private Invitation invitation;
    private Event wedding;
    private Guest guest;

    @BeforeEach
    void setUp() {
        invitation = Invitation.builder()
                .guestId(7L).weddingId(1L).publicToken("tok").status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        guest = Guest.builder().firstName("Jean").lastName("Kabongo")
                .weddingId(1L).allowedCompanions(2).build();
        guest.setId(7L);
        wedding = Event.builder()
                .name("Jean Kabongo & Marie Mukendi")
                
                .status(EventStatus.DRAFT).build();
        wedding.setId(1L);
        wedding.setOrganizationId(100L);
    }

    private Rsvp acceptedRsvp(int attendees) {
        return Rsvp.builder().invitationId(5L).status(RsvpStatus.ACCEPTED).numberOfAttendees(attendees).build();
    }

    private CheckInRequest checkInRequest(int attendees) {
        CheckInRequest req = new CheckInRequest();
        req.setQrToken("tok");
        req.setWeddingId(1L);
        req.setNumberOfAttendees(attendees);
        return req;
    }

    private ScanCheckInRequest scanRequest() {
        ScanCheckInRequest req = new ScanCheckInRequest();
        req.setQrToken("tok");
        req.setWeddingId(1L);
        return req;
    }

    private void stubStateInvitation() {
        when(invitationRepository.findByPublicToken("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));
        when(tableAssignmentRepository.findTableNameByGuestId(7L)).thenReturn(java.util.Optional.empty());
    }

    @Test
    void scan_requiresScanPermission() {
        doThrow(new SecurityException("denied")).when(securityUtils).assertPermission("CHECKIN_SCAN");
        assertThrows(SecurityException.class, () -> checkInService.scan(scanRequest()));
    }

    @Test
    void scan_returnsState_canCheckInTrue() {
        stubStateInvitation();
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(acceptedRsvp(3)));
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(2);

        CheckInScanResponse response = checkInService.scan(scanRequest());

        assertEquals("Jean Kabongo", response.getGuestName());
        assertEquals(3, response.getExpectedAttendees());
        assertEquals(2, response.getCheckedInAttendees());
        assertEquals(1, response.getRemainingAttendees());
        assertTrue(response.isCanCheckIn());
        assertEquals("ACCEPTED", response.getRsvpStatus());
        assertNull(response.getTableName());
    }

    @Test
    void scan_returnsTableName_whenAssigned() {
        stubStateInvitation();
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(acceptedRsvp(3)));
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(0);
        when(tableAssignmentRepository.findTableNameByGuestId(7L)).thenReturn(java.util.Optional.of("Table VIP"));

        CheckInScanResponse response = checkInService.scan(scanRequest());

        assertEquals("Table VIP", response.getTableName());
    }

    @Test
    void scan_canCheckInFalse_whenNoRsvp() {
        stubStateInvitation();
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.empty());
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(0);

        CheckInScanResponse response = checkInService.scan(scanRequest());
        assertEquals(0, response.getExpectedAttendees());
        assertFalse(response.isCanCheckIn());
    }

    @Test
    void scan_unknownToken_404() {
        when(invitationRepository.findByPublicToken("unknown")).thenReturn(Optional.empty());
        ScanCheckInRequest req = new ScanCheckInRequest();
        req.setQrToken("unknown");
        assertThrows(ResourceNotFoundException.class, () -> checkInService.scan(req));
    }

    @Test
    void scan_cancelledInvitation_404() {
        invitation.setStatus(InvitationStatus.CANCELLED);
        when(invitationRepository.findByPublicToken("tok")).thenReturn(Optional.of(invitation));
        assertThrows(ResourceNotFoundException.class, () -> checkInService.scan(scanRequest()));
    }

    @Test
    void scan_wrongWedding_404() {
        // Même organisation, mariage différent : on révèle rien (404 identique).
        when(invitationRepository.findByPublicToken("tok")).thenReturn(Optional.of(invitation));
        ScanCheckInRequest req = scanRequest();
        req.setWeddingId(2L);
        assertThrows(ResourceNotFoundException.class, () -> checkInService.scan(req));
    }

    @Test
    void checkIn_requiresCreatePermission() {
        doThrow(new SecurityException("need")).when(securityUtils).assertPermission("CHECKIN_CREATE");
        assertThrows(SecurityException.class, () -> checkInService.checkIn(checkInRequest(1)));
    }

    @Test
    void checkIn_usesLockedQuery() {
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(acceptedRsvp(3)));
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(1);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(a -> a.getArgument(0));

        checkInService.checkIn(checkInRequest(1));

        verify(invitationRepository).findByPublicTokenForUpdate("tok");
        verify(invitationRepository, never()).findByPublicToken("tok");
    }

    @Test
    void checkIn_creates_whenCapacityAvailable() {
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(acceptedRsvp(3)));
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(1);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(a -> a.getArgument(0));

        CheckInResponse response = checkInService.checkIn(checkInRequest(2));

        ArgumentCaptor<CheckIn> captor = ArgumentCaptor.forClass(CheckIn.class);
        verify(checkInRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getInvitationId());
        assertEquals(Integer.valueOf(2), captor.getValue().getNumberOfAttendees());
        assertNotNull(captor.getValue().getCheckedInAt());
        assertEquals(3, response.getCheckedInAttendees());
        assertEquals(0, response.getRemainingAttendees());
        assertNull(response.getTableName());
    }

    @Test
    void checkIn_returnsTableName_whenAssigned() {
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(acceptedRsvp(3)));
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(0);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(a -> a.getArgument(0));
        when(tableAssignmentRepository.findTableNameByGuestId(7L)).thenReturn(java.util.Optional.of("Table VIP"));

        CheckInResponse response = checkInService.checkIn(checkInRequest(1));

        assertEquals("Table VIP", response.getTableName());
    }

    @Test
    void checkIn_rejects_noRsvp() {
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> checkInService.checkIn(checkInRequest(1)));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void checkIn_rejects_declinedRsvp() {
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(
                Rsvp.builder().invitationId(5L).status(RsvpStatus.DECLINED).numberOfAttendees(0).build()));

        assertThrows(ConflictException.class, () -> checkInService.checkIn(checkInRequest(1)));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void checkIn_rejects_overrun_concurrentLike() {
        // RSVP = 3, déjà entré = 2, demande = 2 → 2 + 2 > 3 → refus (recalcul dans la transaction)
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(rsvpRepository.findByInvitationId(5L)).thenReturn(Optional.of(acceptedRsvp(3)));
        when(checkInRepository.sumByInvitationId(5L)).thenReturn(2);

        assertThrows(ConflictException.class, () -> checkInService.checkIn(checkInRequest(2)));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void checkIn_rejects_zeroAttendees() {
        // parseAttendees lève avant toute résolution (le 0/ négatif est refusé à l'entrée)
        assertThrows(IllegalArgumentException.class, () -> checkInService.checkIn(checkInRequest(0)));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void checkIn_rejects_wrongOrganization() {
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        doThrow(new SecurityException("hors périmètre")).when(securityUtils).assertOrganizationAccess(100L);

        assertThrows(SecurityException.class, () -> checkInService.checkIn(checkInRequest(1)));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void checkIn_rejects_wrongWedding_404() {
        // Même organisation, mariage différent : on révèle rien (404 identique).
        when(invitationRepository.findByPublicTokenForUpdate("tok")).thenReturn(Optional.of(invitation));
        CheckInRequest req = checkInRequest(1);
        req.setWeddingId(2L);

        assertThrows(ResourceNotFoundException.class, () -> checkInService.checkIn(req));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void cancel_requiresCancelPermission() {
        doThrow(new SecurityException("need")).when(securityUtils).assertPermission("CHECKIN_CANCEL");
        assertThrows(SecurityException.class, () -> checkInService.cancel(9L));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void cancel_softDeletes_andLocksInvitation() {
        CheckIn checkIn = CheckIn.builder()
                .invitationId(5L).numberOfAttendees(2).checkedInAt(java.time.LocalDateTime.now()).build();
        checkIn.setId(9L);
        when(checkInRepository.findById(9L)).thenReturn(Optional.of(checkIn));
        when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(a -> a.getArgument(0));

        checkInService.cancel(9L);

        ArgumentCaptor<CheckIn> captor = ArgumentCaptor.forClass(CheckIn.class);
        verify(checkInRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
        verify(invitationRepository).findByIdForUpdate(5L);
        verify(auditService).record(eq("CHECKIN_CANCEL"), eq(9L), eq("CheckIn"), any(), eq(100L), any());
    }

    @Test
    void cancel_unknown_404() {
        when(checkInRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> checkInService.cancel(99L));
    }

    @Test
    void cancel_wrongOrganization_403() {
        CheckIn checkIn = CheckIn.builder()
                .invitationId(5L).numberOfAttendees(1).checkedInAt(java.time.LocalDateTime.now()).build();
        checkIn.setId(9L);
        when(checkInRepository.findById(9L)).thenReturn(Optional.of(checkIn));
        when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(wedding));
        doThrow(new SecurityException("hors périmètre")).when(securityUtils).assertOrganizationAccess(100L);

        assertThrows(SecurityException.class, () -> checkInService.cancel(9L));
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }
}