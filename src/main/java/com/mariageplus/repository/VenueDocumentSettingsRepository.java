package com.mariageplus.repository;

import com.mariageplus.entity.VenueDocumentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueDocumentSettingsRepository extends JpaRepository<VenueDocumentSettings, Long> {
}
