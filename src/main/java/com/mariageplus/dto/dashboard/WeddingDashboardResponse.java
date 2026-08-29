package com.mariageplus.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 *
 * Les champs optionnels sont exclus du JSON selon le rôle de l'utilisateur
 * connecté (voir {@code WeddingDashboardService}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeddingDashboardResponse {

    private Long weddingId;
    private String weddingName;

    private GuestStatisticsResponse guests;
    private InvitationStatisticsResponse invitations;
    private AttendanceStatisticsResponse attendance;
    private TableStatisticsResponse tables;
    private List<CategoryStatisticsResponse> categories;
}