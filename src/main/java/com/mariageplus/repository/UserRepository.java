package com.mariageplus.repository;

import com.mariageplus.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** Serializes login-failure updates for the same account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /**
     * Utilisateurs actifs portant un rôle donné (ex : SUPER_ADMIN pour les
     * notifications de nouvelles organisations). Le filtre soft-delete de
     * BaseEntity s'applique automatiquement.
     */
    @Query("select ur.user from UserRole ur where ur.role.code = :code and ur.user.active = true")
    List<User> findActiveByRoleCode(@Param("code") String code);
}
