package com.mariageplus.repository;

import com.mariageplus.entity.VenueExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VenueExpenseRepository extends JpaRepository<VenueExpense, Long> {

    /** Dépenses de l'organisation pendant [from, to[, les plus récentes d'abord. */
    List<VenueExpense> findByOrganizationIdAndSpentAtGreaterThanEqualAndSpentAtLessThanOrderBySpentAtDescIdDesc(
            Long organizationId, LocalDateTime from, LocalDateTime to);

    Optional<VenueExpense> findByIdAndOrganizationId(Long id, Long organizationId);
}
