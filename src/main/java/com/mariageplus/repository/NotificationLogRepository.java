package com.mariageplus.repository;

import com.mariageplus.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByBatchIdOrderByIdAsc(Long batchId, Pageable pageable);

    /** Dernier journal portant cet identifiant de message Meta (statuts webhook). */
    Optional<NotificationLog> findFirstByMessageIdOrderByIdDesc(String messageId);

    /**
     * Nombre de messages du batch dont la livraison est CONFIRMÉE par le webhook
     * Meta (statuts « delivered » puis « read »). C'est ce qui distingue les
     * messages seulement ACCEPTÉS par l'API des messages réellement remis à
     * l'invité (une facture Meta impayée fait accepter l'envoi sans le livrer).
     */
    long countByBatchIdAndStatusIn(Long batchId, Collection<String> statuses);
}
