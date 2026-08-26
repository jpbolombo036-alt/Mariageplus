package com.mariageplus.service;

import com.mariageplus.entity.Invitation;
import com.mariageplus.entity.InvitationStatus;
import com.mariageplus.entity.WeddingEvent;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.repository.InvitationRepository;
import com.mariageplus.repository.WeddingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Relance automatique des invitations non répondues, à J-X avant le mariage.
 * Chaque jour, pour tous les mariages dont l'événement principal tombe dans la
 * fenêtre de relance ({@code app.invitation.reminder-days-before}), on renvoie un
 * rappel aux invités qui n'ont pas encore répondu (pas de RSVP), dans la limite
 * de {@code max-reminders} par invitation.
 *
 * <p>Protection : chaque relance passe par le même chemin que la relance manuelle
 * (incrément du compteur + limite). Le job est idempotent jour après jour.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationReminderService {

    private final InvitationRepository invitationRepository;
    private final WeddingEventRepository weddingEventRepository;
    private final InvitationService invitationService;

    @Value("${app.invitation.reminder-days-before:7}")
    private int reminderDaysBefore;

    @Value("${app.invitation.reminder-cron:0 0 8 * * *}")
    private String reminderCron;

    @Value("${app.invitation.reminder-enabled:false}")
    private boolean reminderEnabled;

    @Scheduled(cron = "${app.invitation.reminder-cron:0 0 8 * * *}")
    @Transactional
    public void remindPendingInvitations() {
        if (!reminderEnabled) {
            log.debug("Relance automatique désactivée");
            return;
        }
        LocalDate target = LocalDate.now().plusDays(reminderDaysBefore);
        for (WeddingEvent event : weddingEventRepository.findByEventDate(target)) {
            Long weddingId = event.getWeddingId();
            if (weddingId == null) {
                continue;
            }
            relanceWedding(weddingId);
        }
    }

    private void relanceWedding(Long weddingId) {
        int relances = 0;
        for (Invitation invitation : invitationRepository.findNonRespondersByWeddingId(weddingId)) {
            try {
                invitationService.resend(weddingId, invitation.getId());
                relances++;
            } catch (ConflictException | IllegalArgumentException ex) {
                log.debug("Relance ignorée pour l'invitation {} : {}", invitation.getId(), ex.getMessage());
            }
        }
        if (relances > 0) {
            log.info("Relance automatique : {} invitation(s) relancée(s) pour le mariage {}", relances, weddingId);
        }
    }
}