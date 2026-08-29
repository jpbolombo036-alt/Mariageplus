package com.mariageplus.controller;

import com.mariageplus.dto.dashboard.WeddingDashboardResponse;
import com.mariageplus.service.WeddingDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}