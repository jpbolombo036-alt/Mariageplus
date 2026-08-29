package com.mariageplus.repository;

import com.mariageplus.entity.Event;
import com.mariageplus.entity.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
