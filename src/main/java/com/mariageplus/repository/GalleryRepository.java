package com.mariageplus.repository;

import com.mariageplus.entity.Gallery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {
    Optional<Gallery> findByEventId(Long eventId);
}
