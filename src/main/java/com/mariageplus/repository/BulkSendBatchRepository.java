package com.mariageplus.repository;

import com.mariageplus.entity.BulkSendBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des envois en masse. Toujours rattaché à un mariage
 * (le batchId seul n'est jamais accepté : isolation organisation).
 */
@Repository
public interface BulkSendBatchRepository extends JpaRepository<BulkSendBatch, Long> {

    Optional<BulkSendBatch> findByIdAndWeddingId(Long id, Long weddingId);
}
