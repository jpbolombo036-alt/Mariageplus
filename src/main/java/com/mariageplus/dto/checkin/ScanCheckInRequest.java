package com.mariageplus.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Scan d'un QR code : le backend résout l'invitation depuis le jeton public.
 * Le weddingId sert à limiter le scan au mariage actif de l'appli (étanchéité
 * jour J au sein d'une même organisation).
 */
@Data
public class ScanCheckInRequest {

    @NotNull(message = "L'identifiant du mariage est requis")
    private Long weddingId;

    @NotBlank(message = "Le jeton QR est requis")
    private String qrToken;
}