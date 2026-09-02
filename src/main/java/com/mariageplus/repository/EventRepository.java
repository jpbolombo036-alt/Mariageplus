package com.mariageplus.repository;

import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventStatus;
import com.mariageplus.entity.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Repository des événements (nouvelle racine métier). L'isolation est portée
 * par {@code organizationId} et vérifiée côté service.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByOrganizationId(Long organizationId, Pageable pageable);

    Page<Event> findByOrganizationIdAndType(Long organizationId, EventType type, Pageable pageable);

    Page<Event> findByType(EventType type, Pageable pageable);

    List<Event> findByOrganizationId(Long organizationId);

    /** Scoping agent : événements assignés (weddingIds) dans l'organisation courante. */
    Page<Event> findByOrganizationIdAndIdIn(Long organizationId, Collection<Long> ids, Pageable pageable);

    Page<Event> findByOrganizationIdAndTypeAndIdIn(Long organizationId, EventType type, Collection<Long> ids, Pageable pageable);

    /** Événements dans un statut donné dont la date est strictement antérieure à aujourd'hui. */
    List<Event> findByStatusInAndEventDateBefore(Collection<EventStatus> statuses, LocalDate date);
}
