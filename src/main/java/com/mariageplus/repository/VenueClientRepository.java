package com.mariageplus.repository;

import com.mariageplus.entity.VenueClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VenueClientRepository extends JpaRepository<VenueClient, Long> {

    Page<VenueClient> findByOrganizationId(Long organizationId, Pageable pageable);

    /** Recherche par nom, prénom, téléphone ou email (motif déjà en minuscules, avec %). */
    @Query("SELECT c FROM VenueClient c WHERE c.organizationId = :orgId AND ("
            + "LOWER(c.lastName) LIKE :pattern OR LOWER(COALESCE(c.firstName, '')) LIKE :pattern "
            + "OR LOWER(COALESCE(c.email, '')) LIKE :pattern OR COALESCE(c.phone, '') LIKE :phonePattern)")
    Page<VenueClient> search(@Param("orgId") Long organizationId,
                             @Param("pattern") String pattern,
                             @Param("phonePattern") String phonePattern,
                             Pageable pageable);

    List<VenueClient> findByIdIn(Collection<Long> ids);
}
