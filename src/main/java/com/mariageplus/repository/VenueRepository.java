package com.mariageplus.repository;

import com.mariageplus.entity.Venue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findByOrganizationIdOrderByNameAsc(Long organizationId);

    long countByOrganizationIdAndActiveTrue(Long organizationId);

    /**
     * Charge la salle sous verrou d'écriture (SELECT ... FOR UPDATE). Toute
     * création ou modification de réservation sur une salle passe par ce
     * verrou : deux agents qui réservent la même salle en même temps sont
     * sérialisés, et le contrôle de chevauchement voit l'autre réservation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venue v WHERE v.id = :id")
    Optional<Venue> findByIdForUpdate(@Param("id") Long id);
}
