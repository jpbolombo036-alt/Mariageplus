package com.mariageplus.repository;

import com.mariageplus.entity.WeddingDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des détails spécifiques au mariage (1-1 avec {@code Event}).
 */
@Repository
public interface WeddingDetailsRepository extends JpaRepository<WeddingDetails, Long> {

    Optional<WeddingDetails> findByEventId(Long eventId);

    void deleteByEventId(Long eventId);
}
