package com.mariageplus.repository;

import com.mariageplus.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember> findByUser_IdAndActiveTrue(Long userId);

    boolean existsByUser_IdAndOrganization_IdAndActiveTrue(Long userId, Long organizationId);

    boolean existsByUser_IdAndOrganization_IdAndRole_CodeAndActiveTrue(
            Long userId, Long organizationId, String roleCode);

    List<OrganizationMember> findAllByUser_IdAndActiveTrue(Long userId);

    List<OrganizationMember> findByOrganization_Id(Long organizationId);

    /** Membres actifs d'une organisation avec un rôle donné, dont le compte est actif. */
    @Query("select m from OrganizationMember m "
            + "where m.organization.id = :organizationId and m.role.code = :roleCode "
            + "and m.active = true and m.user.active = true")
    List<OrganizationMember> findActiveByOrganizationAndRole(@Param("organizationId") Long organizationId,
                                                             @Param("roleCode") String roleCode);

    boolean existsByUser_IdAndOrganization_Id(Long userId, Long organizationId);

    boolean existsByUser_IdAndRole_IdAndActiveTrue(Long userId, Long roleId);

    boolean existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(Long userId, Long organizationId, Long roleId, Long weddingId);
}
