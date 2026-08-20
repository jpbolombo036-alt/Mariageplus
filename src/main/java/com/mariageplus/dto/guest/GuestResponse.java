package com.mariageplus.dto.guest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestResponse {

    private Long id;
    private Long weddingId;
    private Long categoryId;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String address;
    private Integer allowedCompanions;
    private String notes;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
