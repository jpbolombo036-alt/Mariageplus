package com.mariageplus.repository;

import com.mariageplus.entity.EventDrink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventDrinkRepository extends JpaRepository<EventDrink, Long> {

    List<EventDrink> findByWeddingId(Long weddingId);

    List<EventDrink> findByWeddingIdAndAvailableTrue(Long weddingId);

    Optional<EventDrink> findByWeddingIdAndCatalogItemId(Long weddingId, Long catalogItemId);

    boolean existsByWeddingId(Long weddingId);
}
