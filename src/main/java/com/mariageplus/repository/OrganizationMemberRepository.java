package com.mariageplus.repository;

import com.mariageplus.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember> findByUser_IdAndActiveTrue(Long userId);

    List<OrganizationMember> findAllByUser_IdAndActiveTrue(Long userId);

    List<OrganizationMember> findByOrganization_Id(Long organizationId);

    boolean existsByUser_IdAndOrganization_Id(Long userId, Long organizationId);

    boolean existsByUser_IdAndRole_IdAndActiveTrue(Long userId, Long roleId);

    boolean existsByUser_IdAndOrganization_IdAndRole_IdAndWeddingId(Long userId, Long organizationId, Long roleId, Long weddingId);
}
