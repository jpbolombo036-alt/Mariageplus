package com.mariageplus.repository;

import com.mariageplus.entity.Rsvp;
import com.mariageplus.entity.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository des réponses RSVP. Recherche par invitation (jamais de findAll).
 * Les agrégats statistiques utilisent COUNT/SUM en base (jamais de chargement
 * en mémoire).
 */
@Repository
public interface RsvpRepository extends JpaRepository<Rsvp, Long> {

    Optional<Rsvp> findByInvitationId(Long invitationId);

    boolean existsByInvitationId(Long invitationId);

    @Query("select coalesce(count(r), 0) from Rsvp r " +
            "where r.invitationId in (select i.id from Invitation i where i.weddingId = :weddingId) " +
            "and r.status = :status")
    long countByStatusForWedding(@Param("weddingId") Long weddingId, @Param("status") RsvpStatus status);

    @Query("select coalesce(sum(r.numberOfAttendees), 0) from Rsvp r " +
            "where r.invitationId in (select i.id from Invitation i where i.weddingId = :weddingId) " +
            "and r.status = com.mariageplus.entity.RsvpStatus.ACCEPTED")
    int sumAcceptedAttendeesByWedding(@Param("weddingId") Long weddingId);

    @Query("select coalesce(count(r), 0) from Rsvp r " +
            "where r.invitationId in (select i.id from Invitation i " +
            "  where i.guestId in (select g.id from Guest g where g.weddingId = :weddingId and g.categoryId = :categoryId)) " +
            "and r.status = :status")
    long countByStatusForCategory(@Param("weddingId") Long weddingId,
                                  @Param("categoryId") Long categoryId,
                                  @Param("status") RsvpStatus status);

    @Query("select coalesce(sum(r.numberOfAttendees), 0) from Rsvp r " +
            "where r.invitationId in (select i.id from Invitation i " +
            "  where i.guestId in (select g.id from Guest g where g.weddingId = :weddingId and g.categoryId = :categoryId)) " +
            "and r.status = com.mariageplus.entity.RsvpStatus.ACCEPTED")
    int sumAcceptedAttendeesByCategory(@Param("weddingId") Long weddingId,
                                       @Param("categoryId") Long categoryId);

    /** ADDITIF (GESTIONNAIRE_INVITES) : réponses RSVP des invitations actives d'un mariage. */
    @Query("select r from Rsvp r where r.invitationId in "
            + "(select i.id from Invitation i where i.weddingId = :weddingId and i.deletedAt is null)")
    List<Rsvp> findActiveByWeddingId(@Param("weddingId") Long weddingId);

    List<Rsvp> findByInvitationIdIn(Collection<Long> invitationIds);
}
