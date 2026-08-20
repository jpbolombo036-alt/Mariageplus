package com.mariageplus.repository;

import com.mariageplus.entity.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des invités. Accès conditionné par le mariage parent.
 */
@Repository
public interface GuestRepository extends JpaRepository<Guest, Long> {

    Page<Guest> findByWeddingId(Long weddingId, Pageable pageable);

    List<Guest> findByWeddingId(Long weddingId);

    Optional<Guest> findByIdAndWeddingId(Long id, Long weddingId);

    boolean existsByEmailAndWeddingId(String email, Long weddingId);

    long countByWeddingId(Long weddingId);

    long countByWeddingIdAndCategoryId(Long weddingId, Long categoryId);
}