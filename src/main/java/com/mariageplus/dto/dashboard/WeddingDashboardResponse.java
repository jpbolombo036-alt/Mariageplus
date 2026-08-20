package com.mariageplus.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tableau de bord d'un mariage : agrégats calculés côté backend à partir des
 * données réelles (Guest → Invitation → RSVP → CheckIn, et WeddingTable →
 * TableAssignment). Aucune valeur n'est fournie par le frontend. Réponse en
 * lecture seule, stable pour Flutter/Web.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeddingDashboardResponse {

    private Long weddingId;
    private String weddingName;

    private GuestStatisticsResponse guests;
    private InvitationStatisticsResponse invitations;
    private AttendanceStatisticsResponse attendance;
    private TableStatisticsResponse tables;
    private List<CategoryStatisticsResponse> categories;
}