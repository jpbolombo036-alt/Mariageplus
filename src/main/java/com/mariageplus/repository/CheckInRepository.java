package com.mariageplus.repository;

import com.mariageplus.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Query("select c from CheckIn c where c.deletedAt is null and c.invitationId in " +
            "(select i.id from Invitation i where i.weddingId = :weddingId) " +
            "order by c.checkedInAt desc")
    List<CheckIn> findByWeddingIdOrderByCheckedInAtDesc(@Param("weddingId") Long weddingId);

    /** Dernier check-in d'une invitation (horodatage affiché à l'agent d'accueil). */
    Optional<CheckIn> findTopByInvitationIdOrderByCheckedInAtDesc(Long invitationId);

    /**
     * Idempotence du rejeu hors ligne : la paire (device, sequence) ne doit
     * jamais être insérée deux fois (index unique uq_checkins_device_sequence).
     * Requête native : un check-in annulé (supprimé logiquement) compte aussi,
     * sinon son rejeu violerait l'index unique.
     */
    @Query(value = "SELECT COUNT(*) FROM checkins WHERE device_id = :deviceId AND sequence_number = :sequence",
            nativeQuery = true)
    long countByDeviceAndSequenceIncludingDeleted(@Param("deviceId") String deviceId, @Param("sequence") Long sequenceNumber);

    default boolean existsByDeviceIdAndSequenceNumber(String deviceId, Long sequenceNumber) {
        return countByDeviceAndSequenceIncludingDeleted(deviceId, sequenceNumber) > 0;
    }

    /**
     * Fallback d'idempotence hors ligne pour un appareil qui n'envoie pas de
     * sequence : même invitation, même horodatage de scan, origine hors ligne
     * (check-ins annulés compris, comme ci-dessus).
     */
    @Query(value = "SELECT COUNT(*) FROM checkins WHERE invitation_id = :invitationId "
            + "AND checked_in_at = :checkedInAt AND is_offline = TRUE", nativeQuery = true)
    long countOfflineIncludingDeleted(@Param("invitationId") Long invitationId, @Param("checkedInAt") LocalDateTime checkedInAt);

    default boolean existsByInvitationIdAndCheckedInAtAndOfflineTrue(Long invitationId, LocalDateTime checkedInAt) {
        return countOfflineIncludingDeleted(invitationId, checkedInAt) > 0;
    }
}