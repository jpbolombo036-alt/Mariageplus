package com.mariageplus.dto.wedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeddingResponse {

    private Long id;
    private Long organizationId;
    private String groomFirstName;
    private String groomLastName;
    private String brideFirstName;
    private String brideLastName;
    private String groomPhotoUrl;
    private String bridePhotoUrl;
    private String couplePhotoUrl;
    private String description;
    private String welcomeMessage;
    private String message;
    private String status;
    private String displayName;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
