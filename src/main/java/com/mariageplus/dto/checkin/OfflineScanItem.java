package com.mariageplus.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OfflineScanItem {

    @NotBlank(message = "Le jeton QR est requis")
    private String qrToken;

    @NotNull(message = "Le nombre de personnes est requis")
    private Integer numberOfAttendees;

    private String scannedAt;

    private String deviceId;

    private Long sequence;
}
