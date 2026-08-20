package com.mariageplus.repository;

import com.mariageplus.entity.GuestCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des catégories d'invités. Accès conditionné par le mariage parent.
 */
@Repository
public interface GuestCategoryRepository extends JpaRepository<GuestCategory, Long> {

    Page<GuestCategory> findByWeddingId(Long weddingId, Pageable pageable);

    List<GuestCategory> findByWeddingId(Long weddingId);

    Optional<GuestCategory> findByIdAndWeddingId(Long id, Long weddingId);

    boolean existsByIdAndWeddingId(Long id, Long weddingId);
}
