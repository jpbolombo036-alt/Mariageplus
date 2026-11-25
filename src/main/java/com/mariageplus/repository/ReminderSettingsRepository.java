package com.mariageplus.repository;

import com.mariageplus.entity.ReminderSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, Long> {

    Optional<ReminderSettings> findByScopeAndScopeId(ReminderSettings.Scope scope, Long scopeId);
}
