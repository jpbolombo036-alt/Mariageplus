package com.mariageplus.repository;

import com.mariageplus.entity.AdminNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    /** À utiliser avec un PageRequest trié par createdAt desc. */
    List<AdminNotification> findAllBy(Pageable pageable);

    long countByReadAtIsNull();

    boolean existsByTypeAndUserIdAndCreatedAtAfter(String type, Long userId, LocalDateTime after);

    @Modifying
    @Query("update AdminNotification n set n.readAt = :now where n.readAt is null")
    int markAllRead(@Param("now") LocalDateTime now);
}
