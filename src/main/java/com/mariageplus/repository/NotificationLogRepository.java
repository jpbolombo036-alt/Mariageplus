package com.mariageplus.repository;

import com.mariageplus.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByBatchIdOrderByIdAsc(Long batchId, Pageable pageable);

    /** Dernier journal portant cet identifiant de message Meta (statuts webhook). */
    Optional<NotificationLog> findFirstByMessageIdOrderByIdDesc(String messageId);
}
