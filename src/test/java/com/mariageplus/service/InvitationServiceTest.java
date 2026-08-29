package com.mariageplus.service;

import com.mariageplus.dto.checkin.QrCodeResponse;
import com.mariageplus.dto.invitation.CreateInvitationRequest;
import com.mariageplus.dto.invitation.InvitationResponse;
import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.Event;
import com.mariageplus.service.EventService;
import com.mariageplus.entity.EventSession;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.MailDeliveryException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.InvitationMapper;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private EventRepository eventRepository;
    @Mock private InvitationMapper invitationMapper;
    @Mock private EventService eventService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditService auditService;
    @Mock private QrCodeService qrCodeService;
    @Mock private InvitationMailService invitationMailService;

    @InjectMocks private InvitationService invitationService;

    private Event wedding;
    private Guest guest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invitationService, "maxReminders", 3);
        wedding = Event.builder().organizationId(100L).build();
        wedding.setId(1L);
        guest = Guest.builder().firstName("Jean").lastName("Kabongo").weddingId(1L).build();
        guest.setId(7L);
        lenient().when(invitationMapper.toResponse(any(Invitation.class)))
                .thenReturn(InvitationResponse.builder().build());
    }

    @Test
    void create_GeneratesCodeAndPublicToken_Automatically() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));
        when(invitationRepository.existsByGuestId(7L)).thenReturn(false);
        when(invitationRepository.existsByInvitationCode(anyString())).thenReturn(false);
        when(invitationRepository.existsByPublicToken(anyString())).thenReturn(false);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateInvitationRequest req = new CreateInvitationRequest();
        req.setGuestId(7L);
        invitationService.create(1L, req);

        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        Invitation saved = captor.getValue();
        assertEquals(1L, saved.getWeddingId());
        assertEquals(7L, saved.getGuestId());
        assertEquals(InvitationStatus.GENERATED, saved.getStatus());
        assertTrue(saved.getInvitationCode().startsWith("INV-"));
        assertEquals(32, saved.getPublicToken().length());
        assertNotEquals(String.valueOf(saved.getId()), saved.getPublicToken());
    }

    @Test
    void create_RejectsGuestFromAnotherWedding() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.empty());

        CreateInvitationRequest req = new CreateInvitationRequest();
        req.setGuestId(7L);
        assertThrows(IllegalArgumentException.class, () -> invitationService.create(1L, req));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void create_RejectsDuplicateInvitationForGuest() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));
        when(invitationRepository.existsByGuestId(7L)).thenReturn(true);

        CreateInvitationRequest req = new CreateInvitationRequest();
        req.setGuestId(7L);
        assertThrows(ConflictException.class, () -> invitationService.create(1L, req));
    }
    @Test
    void getById_NotFound_Throws() {
        when(invitationRepository.findByIdAndWeddingId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> invitationService.getById(1L, 99L));
    }

    @Test
    void delete_SoftDeletesInvitation() {
        Invitation invitation = Invitation.builder().weddingId(1L).guestId(7L).build();
        invitation.setId(5L);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        invitationService.delete(1L, 5L);

        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }

    @Test
    void findPublicByToken_ReturnsGuestInfo() {
        Invitation invitation = Invitation.builder().weddingId(1L).guestId(7L)
                .publicToken("tok").status(InvitationStatus.GENERATED).build();
        when(invitationRepository.findByPublicToken("tok")).thenReturn(Optional.of(invitation));
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));

        PublicInvitationResponse response = invitationService.findPublicByToken("tok");
        assertEquals("Jean", response.getGuestFirstName());
        assertEquals("Kabongo", response.getGuestLastName());
        assertEquals("GENERATED", response.getStatus());
    }

    @Test
    void findPublicByToken_Cancelled_Throws() {
        Invitation invitation = Invitation.builder().guestId(7L)
                .status(InvitationStatus.CANCELLED).build();
        when(invitationRepository.findByPublicToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(ResourceNotFoundException.class, () -> invitationService.findPublicByToken("tok"));
    }

    @Test
    void send_withoutEmail_throws() {
        guest.setEmail(null);
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("tok")
                .status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));

        assertThrows(IllegalArgumentException.class, () -> invitationService.send(1L, 5L));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void send_fromGenerated_marksSent_withoutSmtp() {
        guest.setEmail("jean@ex.com");
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("tok")
                .status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));
        when(invitationMailService.publicInviteUrl("tok")).thenReturn("http://localhost:3000/invitations/tok");
        when(invitationMailService.sendInvitation(any(), any(), anyString()))
                .thenReturn(false);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = invitationService.send(1L, 5L);

        assertEquals("SENT", response.getStatus());
        assertFalse(response.isEmailSent());
        assertEquals("http://localhost:3000/invitations/tok", response.getPublicInviteUrl());
        assertNotNull(response.getSentAt());
        assertEquals(InvitationStatus.SENT, invitation.getStatus());
    }

    @Test
    void send_alreadySent_throws() {
        guest.setEmail("jean@ex.com");
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("tok")
                .status(InvitationStatus.SENT).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));

        assertThrows(ConflictException.class, () -> invitationService.send(1L, 5L));
    }

    @Test
    void resend_fromGenerated_throws() {
        guest.setEmail("jean@ex.com");
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("tok")
                .status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));

        assertThrows(ConflictException.class, () -> invitationService.resend(1L, 5L));
    }

    @Test
    void send_mailFailure_doesNotMarkSent() {
        guest.setEmail("jean@ex.com");
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("tok")
                .status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(guestRepository.findByIdAndWeddingId(7L, 1L)).thenReturn(Optional.of(guest));
        when(invitationMailService.publicInviteUrl("tok")).thenReturn("http://x");
        when(invitationMailService.sendInvitation(any(), any(), anyString()))
                .thenThrow(new MailDeliveryException("smtp"));

        assertThrows(MailDeliveryException.class, () -> invitationService.send(1L, 5L));
        verify(invitationRepository, never()).save(any(Invitation.class));
        assertEquals(InvitationStatus.GENERATED, invitation.getStatus());
    }

    @Test
    void cancel_fromGenerated_setsCancelled() {
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        invitationService.cancel(1L, 5L);

        assertEquals(InvitationStatus.CANCELLED, invitation.getStatus());
    }

    @Test
    void cancel_alreadyCancelled_throws() {
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).status(InvitationStatus.CANCELLED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(invitation));

        assertThrows(ConflictException.class, () -> invitationService.cancel(1L, 5L));
    }

    @Test
    void rotateQrToken_requiresUpdatePermission() {
        doThrow(new SecurityException("denied")).when(securityUtils).assertPermission("INVITATION_UPDATE");
        assertThrows(SecurityException.class, () -> invitationService.rotateQrToken(1L, 5L));
    }

    @Test
    void rotateQrToken_changesToken_keepsInvitation_returnsNewQr() {
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("old-token").status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qrCodeService.generateQrDataUri(anyString()))
                .thenAnswer(a -> "data:image/png;base64," + a.getArgument(0));

        QrCodeResponse response = invitationService.rotateQrToken(1L, 5L);

        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        String newToken = captor.getValue().getPublicToken();
        assertNotEquals("old-token", newToken);
        assertEquals(32, newToken.length());
        assertTrue(response.getQrDataUri().contains(newToken));
        assertEquals(InvitationStatus.GENERATED, captor.getValue().getStatus());
        verify(auditService).record(eq("INVITATION_QR_ROTATE"), eq(5L), eq("Invitation"),
                any(), eq(100L), anyString());
    }

    @Test
    void rotateQrToken_unknown_404() {
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> invitationService.rotateQrToken(1L, 99L));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void rotateQrToken_wrongWedding_404() {
        Invitation invitation = Invitation.builder()
                .weddingId(2L).guestId(7L).publicToken("old-token").status(InvitationStatus.GENERATED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
        assertThrows(ResourceNotFoundException.class, () -> invitationService.rotateQrToken(1L, 5L));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void rotateQrToken_cancelled_404() {
        Invitation invitation = Invitation.builder()
                .weddingId(1L).guestId(7L).publicToken("old-token").status(InvitationStatus.CANCELLED).build();
        invitation.setId(5L);
        when(eventService.loadInOrgScope(1L)).thenReturn(wedding);
        when(invitationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invitation));
        assertThrows(ResourceNotFoundException.class, () -> invitationService.rotateQrToken(1L, 5L));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }
}