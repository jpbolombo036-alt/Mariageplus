package com.mariageplus.repository;

import com.mariageplus.entity.WeddingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des événements de mariage. L'accès est toujours conditionné par le
 * mariage parent (dont l'organisation contrôle le périmètre).
 */
@Repository
public interface WeddingEventRepository extends JpaRepository<WeddingEvent, Long> {

    Page<WeddingEvent> findByWeddingId(Long weddingId, Pageable pageable);

    List<WeddingEvent> findByWeddingId(Long weddingId);

    Optional<WeddingEvent> findByIdAndWeddingId(Long id, Long weddingId);

    /** Événement principal : le plus proche dans le temps (futur en premier, sinon le plus récent passé). */
    Optional<WeddingEvent> findFirstByWeddingIdOrderByEventDateAscIdAsc(Long weddingId);

    /** Les événements programmés pour la date cible (relance auto J-X). */
    List<WeddingEvent> findByEventDate(java.time.LocalDate eventDate);

    void deleteByWeddingId(Long weddingId);
}