package com.mariageplus.repository;

import com.mariageplus.entity.TableAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des affectations table/invité. Un seul placement actif par guest
 * ({@code uk_table_assignments_guest}). Le dénombre par table est fait en base
 * (jamais de filtrage findAll en mémoire).
 */
@Repository
public interface TableAssignmentRepository extends JpaRepository<TableAssignment, Long> {

    boolean existsByGuestId(Long guestId);

    Optional<TableAssignment> findByGuestId(Long guestId);

    long countByWeddingTableId(Long weddingTableId);

    @Query("select count(a) from TableAssignment a " +
            "where a.weddingTableId in (select t.id from WeddingTable t where t.weddingId = :weddingId)")
    long countByWeddingId(@Param("weddingId") Long weddingId);
}