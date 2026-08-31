package com.mariageplus.repository;

import com.mariageplus.entity.Drink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrinkRepository extends JpaRepository<Drink, Long> {

    Page<Drink> findByWeddingId(Long weddingId, Pageable pageable);

    List<Drink> findByWeddingIdAndActiveTrue(Long weddingId);

    Optional<Drink> findByIdAndWeddingId(Long id, Long weddingId);

    boolean existsByIdAndWeddingId(Long id, Long weddingId);
}
