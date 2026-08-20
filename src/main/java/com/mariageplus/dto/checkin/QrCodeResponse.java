package com.mariageplus.dto.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QR code d'une invitation : renvoyé sous une forme affichable (data URI PNG).
 * Le token brut n'est pas exposé dans une réponse administrative — la
 * représentation QR suffit au frontend/mobile pour l'affichage et l'impression.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeResponse {

    private String qrDataUri;
}