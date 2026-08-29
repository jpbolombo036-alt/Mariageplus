package com.mariageplus.dto.event;

import lombok.Builder;
import lombok.Data;

/**
 * Réponse des détails spécifiques au mariage (null pour les autres types).
 */
@Data
@Builder
public class WeddingDetailsResponse {
    private Long id;
    private String groomFirstName;
    private String groomLastName;
    private String brideFirstName;
    private String brideLastName;
    private String groomPhotoUrl;
    private String bridePhotoUrl;
    private String couplePhotoUrl;
    private String welcomeMessage;
    private String displayName;
}
