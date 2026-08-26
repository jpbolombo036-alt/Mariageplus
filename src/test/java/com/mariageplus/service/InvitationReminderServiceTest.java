package com.mariageplus.service;

import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.WeddingEvent;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.WeddingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Relance automatique des non-répondants à J-X du mariage : vérifie le déclenchement
 * sur les mariages dont l'événement principal tombe dans la fenêtre, et l'idempotence
 * (jamais de relance quand l'invitation a déjà répondu / dépassé la limite).
 */
@ExtendWith(MockitoExtension.class)
class InvitationReminderServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private WeddingEventRepository weddingEventRepository;
    @Mock private InvitationService invitationService;

    @InjectMocks private InvitationReminderService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reminderEnabled", true);
        ReflectionTestUtils.setField(service, "reminderDaysBefore", 7);
    }

    @Test
    void reminderDisabled_doesNothing() {
        ReflectionTestUtils.setField(service, "reminderEnabled", false);
        service.remindPendingInvitations();
        verify(invitationService, never()).resend(anyLong(), anyLong());
    }

    @Test
    void remind_HitsWeddingsWhoseMainEventIsInWindow() {
        LocalDate target = LocalDate.now().plusDays(7);
        WeddingEvent event = WeddingEvent.builder().weddingId(42L)
                .eventDate(target).build();
        event.setId(99L);
        when(weddingEventRepository.findByEventDate(target)).thenReturn(List.of(event));

        Invitation sent = new Invitation();
        sent.setId(5L);
        when(invitationRepository.findNonRespondersByWeddingId(42L)).thenReturn(List.of(sent));
        when(invitationService.resend(42L, 5L)).thenReturn(
                com.mariageplus.dto.invitation.SendInvitationResponse.builder().build());

        service.remindPendingInvitations();

        verify(invitationService).resend(42L, 5L);
    }

    @Test
    void remind_ignoresInvitationOverLimit() {
        LocalDate target = LocalDate.now().plusDays(7);
        WeddingEvent event = WeddingEvent.builder().weddingId(42L).eventDate(target).build();
        event.setId(99L);
        when(weddingEventRepository.findByEventDate(target)).thenReturn(List.of(event));

        Invitation sent = new Invitation();
        sent.setId(5L);
        when(invitationRepository.findNonRespondersByWeddingId(42L)).thenReturn(List.of(sent));
        when(invitationService.resend(42L, 5L)).thenThrow(new ConflictException("Limite atteinte"));

        service.remindPendingInvitations();

        verify(invitationService, times(1)).resend(42L, 5L);
    }
}