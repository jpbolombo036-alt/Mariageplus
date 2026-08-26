package com.mariageplus.dto.checkin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Enregistrement d'un check-in. Le backend détermine toujours guestId/
 * organisation/capacités côté service — le client fournit le mariage actif, le
 * jeton QR et le nombre de personnes réellement entrées. Le weddingId sert à
 * limiter le check-in au mariage actif de l'appli (étanchéité jour J au sein
 * d'une même organisation).
 */
@Data
public class CheckInRequest {

    @NotNull(message = "L'identifiant du mariage est requis")
    private Long weddingId;

    @NotBlank(message = "Le jeton QR est requis")
    private String qrToken;

    @NotNull(message = "Le nombre de personnes est requis")
    @Min(value = 1, message = "Le nombre de personnes doit être au moins 1")
    private Integer numberOfAttendees;
}