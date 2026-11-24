package com.mariageplus.service;

import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationOpeningTest {
    @Mock private InvitationRepository repository;
    @Mock private EventRepository events;
    @InjectMocks private InvitationService service;

    @Test
    void firstOpeningIsSavedAndRepeatedOpeningPreservesItsDate() {
        Invitation invitation = Invitation.builder().weddingId(1L).status(InvitationStatus.SENT).build();
        when(repository.findByPublicToken("token")).thenReturn(Optional.of(invitation));
        service.markOpened("token");
        var firstOpening = invitation.getOpenedAt();
        assertNotNull(firstOpening);
        service.markOpened("token");
        assertEquals(firstOpening, invitation.getOpenedAt());
        verify(repository, times(1)).save(invitation);
    }

    @Test
    void cancelledInvitationCannotBeMarkedOpened() {
        Invitation invitation = Invitation.builder().status(InvitationStatus.CANCELLED).build();
        when(repository.findByPublicToken("token")).thenReturn(Optional.of(invitation));
        assertThrows(ResourceNotFoundException.class, () -> service.markOpened("token"));
        assertNull(invitation.getOpenedAt());
        verify(repository, never()).save(any());
    }

    @Test
    void unknownInvitationCannotBeMarkedOpened() {
        when(repository.findByPublicToken("unknown")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.markOpened("unknown"));
        verify(repository, never()).save(any());
    }
}