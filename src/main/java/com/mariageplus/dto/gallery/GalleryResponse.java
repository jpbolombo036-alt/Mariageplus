package com.mariageplus.dto.gallery;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class GalleryResponse {
    Long id;
    Long eventId;
    String title;
    String description;
    boolean enabled;
    List<GalleryPhotoResponse> photos;
}
