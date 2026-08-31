package com.mariageplus.controller;

import com.mariageplus.dto.dashboard.ActivityItemResponse;
import com.mariageplus.dto.dashboard.UpcomingSessionResponse;
import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.service.WeddingDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard de statistiques d'un mariage (Étape 9). Lecture seule, isolé par
 * organisation. Les calculs restent dans le service/repositories.
 */
@RestController
@RequestMapping({"/api/weddings/{weddingId}/dashboard", "/api/events/{weddingId}/dashboard"})
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Statistiques d'un mariage (lecture seule, isolé par organisation)")
public class WeddingDashboardController {

    private final WeddingDashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Tableau de bord d'un mariage")
    public ResponseEntity<WeddingDashboardResponse> dashboard(@PathVariable Long weddingId) {
        return ResponseEntity.ok(dashboardService.getDashboard(weddingId));
    }

    @GetMapping("/upcoming-session")
    @Operation(summary = "Prochaine session à venir de l'événement (carte « Prochain événement »)")
    public ResponseEntity<UpcomingSessionResponse> upcomingSession(@PathVariable Long weddingId) {
        UpcomingSessionResponse response = dashboardService.getUpcomingSession(weddingId);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/recent-activity")
    @Operation(summary = "Activité récente de l'organisation (traces d'audit, les plus récentes d'abord)")
    public ResponseEntity<List<ActivityItemResponse>> recentActivity(
            @PathVariable Long weddingId,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentActivity(weddingId, limit));
    }
}
