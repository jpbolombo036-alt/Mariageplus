package com.mariageplus.repository;

import com.mariageplus.entity.WeddingTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des tables. Le nom est unique par mariage (contrainte SQL
 * {@code uk_wedding_tables_name}). Pour les affectations, la table est chargée
 * avec un verrou pessimiste en écriture ({@code PESSIMISTIC_WRITE}) afin de
 * sérialiser deux affectations concurrentes sur la dernière place.
 */
@Repository
public interface WeddingTableRepository extends JpaRepository<WeddingTable, Long> {

    List<WeddingTable> findByWeddingId(Long weddingId);

    Optional<WeddingTable> findByIdAndWeddingId(Long id, Long weddingId);

    boolean existsByWeddingIdAndName(Long weddingId, String name);

    long countByWeddingId(Long weddingId);

    @Query("select coalesce(sum(t.capacity), 0) from WeddingTable t where t.weddingId = :weddingId")
    int sumCapacityByWeddingId(@Param("weddingId") Long weddingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WeddingTable t where t.id = :id")
    Optional<WeddingTable> findByIdForUpdate(@Param("id") Long id);
}