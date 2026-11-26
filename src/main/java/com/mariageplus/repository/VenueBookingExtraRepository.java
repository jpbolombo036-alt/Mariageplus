package com.mariageplus.repository;

import com.mariageplus.entity.VenueBookingExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VenueBookingExtraRepository extends JpaRepository<VenueBookingExtra, Long> {

    List<VenueBookingExtra> findByBookingIdOrderByIdAsc(Long bookingId);

    Optional<VenueBookingExtra> findByIdAndBookingId(Long id, Long bookingId);
}
