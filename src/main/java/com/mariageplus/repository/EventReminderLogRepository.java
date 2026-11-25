package com.mariageplus.repository;

import com.mariageplus.entity.EventReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface EventReminderLogRepository extends JpaRepository<EventReminderLog, Long> {

    boolean existsByEventIdAndKindAndEventDate(Long eventId, String kind, LocalDate eventDate);
}
