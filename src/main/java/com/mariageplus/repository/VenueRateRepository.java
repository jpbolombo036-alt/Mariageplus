package com.mariageplus.repository;

import com.mariageplus.entity.VenueRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VenueRateRepository extends JpaRepository<VenueRate, Long> {

    List<VenueRate> findByVenueIdOrderByLabelAsc(Long venueId);

    Optional<VenueRate> findByIdAndVenueId(Long id, Long venueId);
}
