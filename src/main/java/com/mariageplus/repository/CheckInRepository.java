package com.mariageplus.repository;

import com.mariageplus.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des check-ins. La somme des présences d'une invitation est calculée
 * en base (SUM) — jamais de filtrage findAll en mémoire.
 */
@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    @Query("select coalesce(sum(c.numberOfAttendees), 0) from CheckIn c " +
            "where c.invitationId = :invitationId and c.deletedAt is null")
    int sumByInvitationId(@Param("invitationId") Long invitationId);

    @Query("select coalesce(sum(c.numberOfAttendees), 0) from CheckIn c " +
            "where c.invitationId in (select i.id from Invitation i where i.weddingId = :weddingId)")
    int sumCheckedInByWedding(@Param("weddingId") Long weddingId);

    List<CheckIn> findByInvitationId(Long invitationId);
}