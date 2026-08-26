package com.mariageplus.service;

import com.mariageplus.dto.invitation.PublicInvitationResponse;
import com.mariageplus.dto.rsvp.PublicRsvpResponse;
import com.mariageplus.dto.rsvp.SubmitRsvpRequest;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import com.mariageplus.entity.Wedding;
import com.mariageplus.entity.WeddingStatus;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.RsvpRepository;
import com.mariageplus.repository.WeddingEventRepository;
import com.mariageplus.repository.WeddingRepository;
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

@ExtendWith(MockitoExtension.class)
class RsvpServiceTest {

    @Mock private RsvpRepository rsvpRepository;
    @Mock private InvitationService invitationService;
    @Mock private GuestRepository guestRepository;
    @Mock private WeddingRepository weddingRepository;
    @Mock private WeddingEventRepository weddingEventRepository;

    @InjectMocks private RsvpService rsvpService;

    private Invitation invitation;
    private Guest guest;

    @BeforeEach
    void setUp() {
        invitation = Invitation.builder().guestId(7L).weddingId(1L).status(InvitationStatus.GENERATED).build();
        invitation.setId(1L);
        guest = Guest.builder().firstName("Jean").lastName("Kabongo").weddingId(1L)
                .allowedCompanions(2).build(); // maximumAllowed = 3
        guest.setId(7L);
    }

    private void stubGuestAndInvitation() {
        when(invitationService.resolvePublicInvitation("tok")).thenReturn(invitation);
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));
    }

    private SubmitRsvpRequest request(String status, Integer number) {
        SubmitRsvpRequest req = new SubmitRsvpRequest();
        req.setStatus(status);
        req.setNumberOfAttendees(number);
        return req;
    }

    @Test
    void getPublicPage_mapsWeddingData_andCapacity() {
        when(invitationService.resolvePublicInvitation("tok")).thenReturn(invitation);
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));
        when(weddingRepository.findById(1L)).thenReturn(Optional.of(Wedding.builder()
                .groomFirstName("Jean").groomLastName("Kabongo")
                .brideFirstName("Marie").brideLastName("Mukendi")
                .couplePhotoUrl("http://x/photo.jpg").message("Venez en bleu")
                .status(WeddingStatus.DRAFT).build()));
        when(weddingEventRepository.findFirstByWeddingIdOrderByEventDateAscIdAsc(1L))
                .thenReturn(Optional.empty());

        var page = rsvpService.getPublicPage("tok");

        assertEquals("Jean Kabongo & Marie Mukendi", page.getWeddingDisplayName());
        assertEquals("http://x/photo.jpg", page.getCouplePhotoUrl());
        assertEquals("Venez en bleu", page.getMessage());
        assertEquals(3, page.getMaxAccepted()); // 1 + 2 accompagnants
        assertNull(page.getRsvpStatus());
        assertTrue(page.isCanRespond());
    }

    @Test
    void getPublicInvitation_IncludesRsvpStatus_whenExists() {
        when(invitationService.resolvePublicInvitation("token")).thenReturn(invitation);
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));
        when(weddingRepository.findById(1L)).thenReturn(Optional.of(Wedding.builder()
                .groomFirstName("Jean").groomLastName("Kabongo")
                .brideFirstName("Marie").brideLastName("Mukendi")
                .status(WeddingStatus.DRAFT).build()));
        when(rsvpRepository.findByInvitationId(1L)).thenReturn(Optional.of(
                Rsvp.builder().invitationId(1L).status(RsvpStatus.ACCEPTED).numberOfAttendees(2).build()));

        PublicInvitationResponse response = rsvpService.getPublicInvitation("token");
        assertEquals("ACCEPTED", response.getRsvpStatus());
        assertEquals(Integer.valueOf(2), response.getRsvpNumberOfAttendees());
        assertEquals("Jean", response.getGuestFirstName());
        assertEquals("GENERATED", response.getStatus());
        assertEquals("Jean Kabongo & Marie Mukendi", response.getWeddingDisplayName());
    }

    @Test
    void getPublicInvitation_RsvpStatusNull_whenNoRsvp() {
        when(invitationService.resolvePublicInvitation("tok")).thenReturn(invitation);
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));
        when(rsvpRepository.findByInvitationId(1L)).thenReturn(Optional.empty());

        PublicInvitationResponse response = rsvpService.getPublicInvitation("tok");
        assertNull(response.getRsvpStatus());
        assertNull(response.getRsvpNumberOfAttendees());
    }

    @Test
    void getPublicInvitation_PropagatesNotFound() {
        when(invitationService.resolvePublicInvitation("bad")).thenThrow(new ResourceNotFoundException("x"));
        assertThrows(ResourceNotFoundException.class, () -> rsvpService.getPublicInvitation("bad"));
    }

    @Test
    void submitRsvp_CreatesNew_whenAbsent() {
        stubGuestAndInvitation();
        when(rsvpRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(rsvpRepository.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));

        PublicRsvpResponse response = rsvpService.submitRsvp("tok", request("ACCEPTED", 3));

        ArgumentCaptor<Rsvp> captor = ArgumentCaptor.forClass(Rsvp.class);
        verify(rsvpRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getInvitationId());
        assertEquals(RsvpStatus.ACCEPTED, captor.getValue().getStatus());
        assertEquals(Integer.valueOf(3), captor.getValue().getNumberOfAttendees());
        assertNotNull(captor.getValue().getRespondedAt());
        assertEquals("ACCEPTED", response.getRsvpStatus());
        assertEquals(Integer.valueOf(3), response.getNumberOfAttendees());
    }

    @Test
    void submitRsvp_UpdatesExisting_KeepsSingleRow() {
        Rsvp existing = Rsvp.builder().invitationId(1L).status(RsvpStatus.DECLINED).numberOfAttendees(0).build();
        stubGuestAndInvitation();
        when(rsvpRepository.findByInvitationId(1L)).thenReturn(Optional.of(existing));
        when(rsvpRepository.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));

        rsvpService.submitRsvp("tok", request("ACCEPTED", 2));

        ArgumentCaptor<Rsvp> captor = ArgumentCaptor.forClass(Rsvp.class);
        verify(rsvpRepository).save(captor.capture());
        assertEquals(RsvpStatus.ACCEPTED, captor.getValue().getStatus());
        assertEquals(Integer.valueOf(2), captor.getValue().getNumberOfAttendees());
        verify(rsvpRepository, never()).save(argThat(r -> false));
    }

    @Test
    void submitRsvp_InvalidValue_Throws() {
        stubGuestAndInvitation();
        SubmitRsvpRequest req = request("MAYBE", 1);
        assertThrows(IllegalArgumentException.class, () -> rsvpService.submitRsvp("tok", req));
        verify(rsvpRepository, never()).save(any(Rsvp.class));
    }

    @Test
    void submitRsvp_AcceptedAttendeesInsideBounds_Valid() {
        // max = 3 → 1, 2 et 3 acceptés
        stubGuestAndInvitation();
        when(rsvpRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(rsvpRepository.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("ACCEPTED", rsvpService.submitRsvp("tok", request("ACCEPTED", 1)).getRsvpStatus());
        assertEquals("ACCEPTED", rsvpService.submitRsvp("tok", request("ACCEPTED", 2)).getRsvpStatus());
        assertEquals("ACCEPTED", rsvpService.submitRsvp("tok", request("ACCEPTED", 3)).getRsvpStatus());
    }

    @Test
    void submitRsvp_AcceptedAttendeesZero_Throws() {
        stubGuestAndInvitation();
        SubmitRsvpRequest req = request("ACCEPTED", 0);
        assertThrows(IllegalArgumentException.class, () -> rsvpService.submitRsvp("tok", req));
        verify(rsvpRepository, never()).save(any(Rsvp.class));
    }

    @Test
    void submitRsvp_AcceptedAttendeesExceedsMax_Throws() {
        stubGuestAndInvitation();
        SubmitRsvpRequest req = request("ACCEPTED", 4);
        assertThrows(IllegalArgumentException.class, () -> rsvpService.submitRsvp("tok", req));
        verify(rsvpRepository, never()).save(any(Rsvp.class));
    }

    @Test
    void submitRsvp_DeclinedAttendeesZero_Valid() {
        stubGuestAndInvitation();
        when(rsvpRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(rsvpRepository.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("DECLINED", rsvpService.submitRsvp("tok", request("DECLINED", 0)).getRsvpStatus());
    }

    @Test
    void submitRsvp_DeclinedAttendeesPositive_Throws() {
        stubGuestAndInvitation();
        SubmitRsvpRequest req = request("DECLINED", 1);
        assertThrows(IllegalArgumentException.class, () -> rsvpService.submitRsvp("tok", req));
        verify(rsvpRepository, never()).save(any(Rsvp.class));
    }

    @Test
    void submitRsvp_NullAttendees_Throws() {
        stubGuestAndInvitation();
        SubmitRsvpRequest req = request("ACCEPTED", null);
        assertThrows(IllegalArgumentException.class, () -> rsvpService.submitRsvp("tok", req));
        verify(rsvpRepository, never()).save(any(Rsvp.class));
    }
}