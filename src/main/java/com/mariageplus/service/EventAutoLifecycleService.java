package com.mariageplus.service;

import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Cycle de vie automatique des événements dont la date est passée.
 *
 * <p>Les événements ne changent pas de statut tout seuls : le modèle de transition
 * (DRAFT → PUBLISHED → ACTIVE → COMPLETED → ARCHIVED) est piloté manuellement.
 * Ce job rattrape automatiquement la partie « date » de cette logique chaque nuit
 * (désactivable, désactivé par défaut) :
 * <ul>
 *   <li>PUBLISHED et date du jour dépassée → ACTIVE (le jour J est là) ;</li>
 *   <li>ACTIVE et date du jour dépassée → COMPLETED (la journée est passée).</li>
 * </ul>
 * Les événements restants (COMPLETED / ARCHIVED) et les régressions (ARCHIVED,
 * CANCELLED) ne sont jamais touchés. Le job respecte donc le graphe de transition
 * existant et reste idempotent jour après jour.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventAutoLifecycleService {

    private final EventRepository eventRepository;

    @Value("${app.event.autocomplete-enabled:false}")
    private boolean autoEnabled;

    @Scheduled(cron = "${app.event.autocomplete-cron:0 15 0 * * *}")
    @Transactional
    public void autoAdvancePastEvents() {
        if (!autoEnabled) {
            log.debug("Cycle de vie automatique des événements désactivé");
            return;
        }
        LocalDate today = LocalDate.now();
        List<Event> toAdvance = eventRepository.findByStatusInAndEventDateBefore(
                List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE), today);

        int updated = 0;
        for (Event event : toAdvance) {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                event.setStatus(EventStatus.ACTIVE);
            } else if (event.getStatus() == EventStatus.ACTIVE) {
                event.setStatus(EventStatus.COMPLETED);
            }
            eventRepository.save(event);
            updated++;
        }
        if (updated > 0) {
            log.info("Cycle de vie automatique : {} événement(s) passé(s) dans le statut suivant", updated);
        }
    }
}