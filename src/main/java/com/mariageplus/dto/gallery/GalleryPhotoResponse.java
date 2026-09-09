package com.mariageplus.dto.gallery;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GalleryPhotoResponse {
    Long id;
    Long galleryId;
    Long guestCategoryId;
    String caption;
    Integer displayOrder;
    String contentType;
    long size;
    String imageUrl;
}
