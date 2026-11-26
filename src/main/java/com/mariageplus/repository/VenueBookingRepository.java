package com.mariageplus.repository;

import com.mariageplus.entity.VenueBooking;
import com.mariageplus.entity.VenueBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VenueBookingRepository extends JpaRepository<VenueBooking, Long>,
        JpaSpecificationExecutor<VenueBooking> {

    /**
     * Réservations de la salle qui chevauchent [start, end[ avec un statut
     * bloquant. {@code excludeId} = la réservation en cours de modification
     * (-1 à la création).
     */
    @Query("SELECT b FROM VenueBooking b WHERE b.venueId = :venueId AND b.status IN :statuses "
            + "AND b.startAt < :end AND b.endAt > :start AND b.id <> :excludeId ORDER BY b.startAt")
    List<VenueBooking> findOverlapping(@Param("venueId") Long venueId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("statuses") Collection<VenueBookingStatus> statuses,
                                       @Param("excludeId") Long excludeId);

    /** Réservations d'une salle qui touchent la période [from, to[ (calendrier). */
    @Query("SELECT b FROM VenueBooking b WHERE b.venueId = :venueId "
            + "AND b.startAt < :to AND b.endAt > :from ORDER BY b.startAt")
    List<VenueBooking> findInPeriod(@Param("venueId") Long venueId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /** Réservations de l'organisation qui touchent la période [from, to[. */
    @Query("SELECT b FROM VenueBooking b WHERE b.organizationId = :orgId "
            + "AND b.startAt < :to AND b.endAt > :from ORDER BY b.startAt")
    List<VenueBooking> findByOrganizationInPeriod(@Param("orgId") Long organizationId,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    List<VenueBooking> findByOrganizationIdAndStatusIn(Long organizationId, Collection<VenueBookingStatus> statuses);

    List<VenueBooking> findByClientIdOrderByStartAtDesc(Long clientId);

    List<VenueBooking> findByStatusAndOptionExpiresAtBefore(VenueBookingStatus status, LocalDateTime limit);

    Optional<VenueBooking> findFirstByEventId(Long eventId);
}
