package com.mariageplus.repository;

import com.mariageplus.entity.Venue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
    /**
     * Salles supprimées (logiquement) de l'organisation. Requête native : le
     * filtre @SQLRestriction("deleted_at IS NULL") de l'entité ne s'y applique pas.
     */
    @Query(value = "SELECT * FROM venues WHERE organization_id = :orgId AND deleted_at IS NOT NULL "
            + "ORDER BY deleted_at DESC", nativeQuery = true)
    List<Venue> findDeletedByOrganizationId(@Param("orgId") Long organizationId);

    /** Annule la suppression logique d'une salle de l'organisation ; retourne 0 si introuvable. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE venues SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND organization_id = :orgId AND deleted_at IS NOT NULL", nativeQuery = true)
    int restore(@Param("id") Long id, @Param("orgId") Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venue v WHERE v.id = :id")
    Optional<Venue> findByIdForUpdate(@Param("id") Long id);
}
