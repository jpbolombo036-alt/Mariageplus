package com.mariageplus.dto.gallery;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GallerySettingsRequest {
    @Size(max = 200)
    private String title;
    @Size(max = 2000)
    private String description;
    private Boolean enabled;
}
