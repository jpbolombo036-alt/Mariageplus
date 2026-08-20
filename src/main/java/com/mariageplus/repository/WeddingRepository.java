package com.mariageplus.repository;

import com.mariageplus.entity.Wedding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des mariages. Les accès des utilisateurs non globaux doivent être
 * filtrés par organisation directement en base (jamais de filtrage en mémoire).
 */
@Repository
public interface WeddingRepository extends JpaRepository<Wedding, Long> {

    Page<Wedding> findByOrganizationId(Long organizationId, Pageable pageable);

    Optional<Wedding> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByOrganizationId(Long organizationId);
}