package com.mariageplus.repository;

import com.mariageplus.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUser_Id(Long userId);

    boolean existsByUser_IdAndRole_Id(Long userId, Long roleId);

    boolean existsByRoleId(Long roleId);

    @Query("select r.code from UserRole ur join ur.role r where ur.user.id = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Query("select r.id from UserRole ur join ur.role r where ur.user.id = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);
}
