package com.mariageplus.repository;

import com.mariageplus.entity.VenueExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VenueExchangeRateRepository extends JpaRepository<VenueExchangeRate, Long> {

    Optional<VenueExchangeRate> findFirstByOrganizationIdOrderByEffectiveAtDescIdDesc(Long organizationId);

    List<VenueExchangeRate> findTop30ByOrganizationIdOrderByEffectiveAtDescIdDesc(Long organizationId);
}
