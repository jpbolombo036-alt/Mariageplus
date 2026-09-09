package com.mariageplus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "gallery_photos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GalleryPhoto extends BaseEntity {
    @Column(name = "gallery_id", nullable = false)
    private Long galleryId;

    /** Null means visible to every valid invitation of the event. */
    @Column(name = "guest_category_id")
    private Long guestCategoryId;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "image")
    private byte[] image;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(length = 500)
    private String caption;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
