package com.mariageplus.repository;

import com.mariageplus.entity.VenueBookingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VenueBookingPaymentRepository extends JpaRepository<VenueBookingPayment, Long> {

    List<VenueBookingPayment> findByBookingIdOrderByPaidAtAscIdAsc(Long bookingId);

    Optional<VenueBookingPayment> findByIdAndBookingId(Long id, Long bookingId);

    boolean existsByBookingId(Long bookingId);

    /** Total encaissé (converti dans la devise de la réservation) pour chaque réservation. */
    @Query("SELECT p.bookingId AS bookingId, SUM(p.amountInBookingCurrency) AS paid "
            + "FROM VenueBookingPayment p WHERE p.bookingId IN :ids GROUP BY p.bookingId")
    List<PaidTotal> sumPaidByBookingIds(@Param("ids") Collection<Long> bookingIds);

    /** Paiements encaissés par l'organisation pendant [from, to[. */
    @Query("SELECT p FROM VenueBookingPayment p WHERE p.bookingId IN "
            + "(SELECT b.id FROM VenueBooking b WHERE b.organizationId = :orgId) "
            + "AND p.paidAt >= :from AND p.paidAt < :to ORDER BY p.paidAt")
    List<VenueBookingPayment> findByOrganizationPaidBetween(@Param("orgId") Long organizationId,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("to") LocalDateTime to);

    interface PaidTotal {
        Long getBookingId();

        BigDecimal getPaid();
    }
}
