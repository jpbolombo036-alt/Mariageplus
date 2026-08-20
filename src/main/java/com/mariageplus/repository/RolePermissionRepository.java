package com.mariageplus.repository;

import com.mariageplus.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRole_Id(Long roleId);

    void deleteByRole_Id(Long roleId);

    @Query("select distinct p.code from RolePermission rp join rp.permission p where rp.role.id in :roleIds")
    List<String> findCodesByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
