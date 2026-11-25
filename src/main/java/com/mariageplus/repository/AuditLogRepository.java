package com.mariageplus.repository;

import com.mariageplus.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Dernières traces d'une organisation (pour l'« Activité récente » du
     * dashboard). Utiliser avec un PageRequest trié par performedAt desc.
     */
    List<AuditLog> findByOrganizationId(Long organizationId, Pageable pageable);

    /** Même chose sans les traces techniques (connexions, sécurité, plateforme). */
    List<AuditLog> findByOrganizationIdAndActionNotIn(Long organizationId, Collection<String> actions, Pageable pageable);
}

