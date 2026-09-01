package com.mariageplus.repository;

import com.mariageplus.entity.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Recherche invité pour l'agent d'accueil : nom/prénom (concaténés), téléphone
     * ou email contenant la requête. Le soft-delete est exclu par le filtre
     * global de BaseEntity.
     */
    @Query("""
            select g from Guest g
            where g.weddingId = :weddingId
              and (lower(concat(coalesce(g.firstName, ''), ' ', coalesce(g.lastName, ''))) like lower(concat('%', :q, '%'))
                or lower(coalesce(g.phone, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(g.email, '')) like lower(concat('%', :q, '%')))
            order by g.firstName asc, g.lastName asc
            """)
    List<Guest> searchByWeddingIdAndQuery(@Param("weddingId") Long weddingId,
                                          @Param("q") String q,
                                          Pageable pageable);
}