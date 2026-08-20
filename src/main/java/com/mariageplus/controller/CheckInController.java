package com.mariageplus.controller;

import com.mariageplus.dto.checkin.CheckInRequest;
import com.mariageplus.dto.checkin.CheckInResponse;
import com.mariageplus.dto.checkin.CheckInScanResponse;
import com.mariageplus.dto.checkin.ScanCheckInRequest;
import com.mariageplus.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Check-in : scan QR (état) et enregistrement d'entrées. Accessible aux agents
 * disposant de CHECKIN_SCAN / CHECKIN_CREATE, isolé par organisation.
 */
@RestController
@RequestMapping("/api/checkins")
@RequiredArgsConstructor
@Tag(name = "Check-in", description = "Scan QR et enregistrement des entrées (isolé par organisation)")
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/scan")
    @Operation(summary = "Scanner un QR et retourner l'état de l'invitation")
    public ResponseEntity<CheckInScanResponse> scan(@Valid @RequestBody ScanCheckInRequest request) {
        return ResponseEntity.ok(checkInService.scan(request));
    }

    @PostMapping
    @Operation(summary = "Enregistrer une entrée (check-in) dans la limite du RSVP")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkInService.checkIn(request));
    }

    @DeleteMapping("/{checkInId}")
    @Operation(summary = "Annuler un check-in (la place est recrédité)")
    public ResponseEntity<Void> cancel(@PathVariable Long checkInId) {
        checkInService.cancel(checkInId);
        return ResponseEntity.noContent().build();
    }
}