package com.mariageplus.service;

import com.mariageplus.entity.BulkSendBatch;
import com.mariageplus.entity.Event;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.exception.WhatsAppDeliveryException;
import com.mariageplus.repository.BulkSendBatchRepository;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkSendWorkerTest {

    @Mock private BulkSendBatchRepository batchRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private NotificationLogRepository logRepository;
    @Mock private EventRepository eventRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private InvitationMailService invitationMailService;
    @Mock private AuditService auditService;

    @InjectMocks
    private BulkSendWorker worker;

    private BulkSendBatch batch;
    private Event event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "delayMs", 0L);
        ReflectionTestUtils.setField(worker, "defaultCountryCode", "225");
        batch = BulkSendBatch.builder().weddingId(1L).organizationId(5L)
                .channel("WHATSAPP").status("PENDING").totalCount(2).createdBy(7L).build();
        batch.setId(9L);
        event = Event.builder().name("Josué & Eunice").build();
        event.setId(1L);
    }

    private void stubCommon(Invitation invitation, Guest guest) {
        lenient().when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
        lenient().when(batchRepository.save(any(BulkSendBatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        lenient().when(invitationRepository.findById(11L)).thenReturn(Optional.of(invitation));
        lenient().when(guestRepository.findById(21L)).thenReturn(Optional.ofNullable(guest));
        lenient().when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invitationMailService.publicInviteUrl("tok"))
                .thenReturn("https://front/invitations/tok");
    }

    @Test
    void validPhone_sendsAndMarksInvitationSent() {
        Invitation invitation = Invitation.builder().guestId(21L)
                .weddingId(1L).publicToken("tok").status(InvitationStatus.GENERATED).build();
        invitation.setId(11L);
        Guest guest = Guest.builder().firstName("Claire").phone("07 01 02 03 04").build();
        guest.setId(21L);
        stubCommon(invitation, guest);
        // 07 01 02 03 04 (local) → +225 701020304 → id WhatsApp "225701020304"
        when(whatsAppService.sendInvitationTemplate(eq("225701020304"), any(), any(), anyString(), isNull()))
                .thenReturn(true);

        worker.processBatch(9L, 1L, List.of(11L), null, false);

        assertEquals(InvitationStatus.SENT, invitation.getStatus());
        assertNotNull(invitation.getSentAt());
        verify(auditService).record(eq("INVITATION_BULK_SEND"), eq(9L), eq("BulkSendBatch"),
                eq(7L), eq(5L), anyString());
    }

    @Test
    void missingPhone_skipsWithoutApiCall() {
        Invitation invitation = Invitation.builder().guestId(21L)
                .weddingId(1L).publicToken("tok").status(InvitationStatus.GENERATED).build();
        invitation.setId(11L);
        Guest guest = Guest.builder().firstName("Sans").phone(null).build();
        guest.setId(21L);
        stubCommon(invitation, guest);

        worker.processBatch(9L, 1L, List.of(11L), null, false);

        verify(whatsAppService, never()).sendInvitationTemplate(any(), any(), any(), any(), any());
        assertEquals(InvitationStatus.GENERATED, invitation.getStatus());
        assertEquals(1, batch.getSkippedCount());
    }

    @Test
    void apiFailure_countsFailedAndLogsReason() {
        Invitation invitation = Invitation.builder().guestId(21L)
                .weddingId(1L).publicToken("tok").status(InvitationStatus.GENERATED).build();
        invitation.setId(11L);
        Guest guest = Guest.builder().firstName("Claire").phone("+2250701020304").build();
        guest.setId(21L);
        stubCommon(invitation, guest);
        when(whatsAppService.sendInvitationTemplate(any(), any(), any(), anyString(), isNull()))
                .thenThrow(new WhatsAppDeliveryException("Recipient not in whatsapp"));

        worker.processBatch(9L, 1L, List.of(11L), null, false);

        assertEquals(1, batch.getFailedCount());
        assertEquals(0, batch.getSentCount());
        assertEquals("COMPLETED", batch.getStatus());
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void resendMode_incrementsReminderCount() {
        Invitation invitation = Invitation.builder().guestId(21L)
                .weddingId(1L).publicToken("tok")
                .status(InvitationStatus.SENT).reminderCount(1).build();
        invitation.setId(11L);
        Guest guest = Guest.builder().firstName("Claire").phone("+2250701020304").build();
        guest.setId(21L);
        stubCommon(invitation, guest);
        when(whatsAppService.sendInvitationTemplate(any(), any(), any(), anyString(), isNull()))
                .thenReturn(true);

        worker.processBatch(9L, 1L, List.of(11L), null, true);

        assertEquals(2, invitation.getReminderCount());
        assertEquals(1, batch.getSentCount());
    }

    @Test
    void interruptedThread_marksBatchFailed() throws Exception {
        Invitation invitation = Invitation.builder().guestId(21L)
                .weddingId(1L).publicToken("tok").status(InvitationStatus.GENERATED).build();
        invitation.setId(11L);
        Guest guest = Guest.builder().firstName("Claire").phone("07 01 02 03 04").build();
        guest.setId(21L);
        stubCommon(invitation, guest);
        ReflectionTestUtils.setField(worker, "delayMs", 1L);

        Thread.currentThread().interrupt();

        worker.processBatch(9L, 1L, List.of(11L), null, false);

        assertEquals("FAILED", batch.getStatus());
        verify(batchRepository, atLeastOnce()).save(any(BulkSendBatch.class));
    }

    @Test
    void missingEvent_batchFails() {
        when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any(BulkSendBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        worker.processBatch(9L, 1L, List.of(11L), null, false);

        assertEquals("FAILED", batch.getStatus());
    }
}
