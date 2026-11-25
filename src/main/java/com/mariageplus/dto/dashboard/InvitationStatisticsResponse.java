package com.mariageplus.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationStatisticsResponse {

    private long total;
    private long accepted;
    private long declined;
    private long pending;
    private double responseRate;
    /** Invitations envoyées au moins une fois. */
    private long sent;
    /** Invitations dont l'invité a ouvert la page (suivi d'ouverture). */
    private long opened;
}