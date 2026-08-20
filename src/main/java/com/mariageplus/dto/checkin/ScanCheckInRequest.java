package com.mariageplus.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Scan d'un QR code : le backend résout l'invitation depuis le jeton public.
 */
@Data
public class ScanCheckInRequest {

    @NotBlank(message = "Le jeton QR est requis")
    private String qrToken;
}