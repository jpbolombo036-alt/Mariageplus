package com.mariageplus.dto.checkin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SyncCheckInRequest {

    @NotNull(message = "L'identifiant de l'événement est requis")
    private Long eventId;

    @NotEmpty(message = "La liste des scans ne peut pas être vide")
    @Size(max = 500, message = "Trop de scans dans ce batch (max 500)")
    private List<OfflineScanItem> scans;
}
