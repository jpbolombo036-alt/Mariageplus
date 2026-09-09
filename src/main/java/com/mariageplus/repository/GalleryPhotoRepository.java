package com.mariageplus.repository;

import com.mariageplus.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, Long> {
    List<GalleryPhoto> findByGalleryIdOrderByDisplayOrderAscIdAsc(Long galleryId);
    Optional<GalleryPhoto> findByIdAndGalleryId(Long id, Long galleryId);
}
