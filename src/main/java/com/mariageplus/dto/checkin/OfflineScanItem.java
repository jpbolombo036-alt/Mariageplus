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

    /**
     * Horodatage ISO-8601 du scan côté appareil (ex. 2026-10-06T14:30:00).
     * Borné côté serveur : ni futur (tolérance d'horloge), ni antérieur au jour
     * de l'événement.
     */
    private String scannedAt;

    private String deviceId;

    /**
     * Compteur monotone PAR APPAREIL, jamais remis à zéro (même après
     * redémarrage ou changement d'événement) : clé d'idempotence du rejeu,
     * couverte par l'index unique (device_id, sequence_number) en base.
     */
    private Long sequence;
}
