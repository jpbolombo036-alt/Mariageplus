package com.mariageplus.repository;

import com.mariageplus.entity.EventSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des sessions (sous-étapes) d'un événement. L'accès est toujours
 * conditionné par l'événement parent (dont l'organisation contrôle le périmètre).
 */
@Repository
public interface EventSessionRepository extends JpaRepository<EventSession, Long> {

    Page<EventSession> findByEventId(Long eventId, Pageable pageable);

    List<EventSession> findByEventId(Long eventId);

    /** Sessions actives d'un événement, triées pour le programme public (date, heure, ordre). */
    List<EventSession> findByEventIdAndActiveTrueOrderBySessionDateAscStartTimeAscDisplayOrderAscIdAsc(Long eventId);

    List<EventSession> findBySessionDateAndActiveTrue(java.time.LocalDate date);

    Optional<EventSession> findByIdAndEventId(Long id, Long eventId);

    /**
     * Prochaine session à venir d'un événement (sessionDate >= date fournie),
     * triée par date puis heure de début — pour la carte « Prochain événement ».
     * Le filtre deleted_at IS NULL est appliqué par @SQLRestriction de BaseEntity.
     */
    Optional<EventSession> findFirstByEventIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAscIdAsc(
            Long eventId, java.time.LocalDate date);

    void deleteByEventId(Long eventId);
}
