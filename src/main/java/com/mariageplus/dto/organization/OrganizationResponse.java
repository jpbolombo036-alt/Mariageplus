package com.mariageplus.dto.organization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private boolean active;
    private LocalDateTime createdAt;
}