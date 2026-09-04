package com.mariageplus.controller;

import com.mariageplus.dto.bulksend.BulkSendBatchResponse;
import com.mariageplus.dto.bulksend.BulkSendRequest;
import com.mariageplus.dto.bulksend.NotificationLogResponse;
import com.mariageplus.dto.PageResponse;
import com.mariageplus.service.BulkSendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Envoi en masse des invitations (WhatsApp). Le démarrage répond 202 avec le
 * batch créé ; le traitement se poursuit en arrière-plan — le front interroge
 * le batch et son journal pour afficher la progression et les échecs.
 */
@RestController
@RequestMapping({"/api/weddings/{weddingId}/invitations", "/api/events/{weddingId}/invitations"})
@RequiredArgsConstructor
@Tag(name = "Invitations — envoi en masse", description = "Envoi WhatsApp groupé des invitations (batch asynchrone)")
public class BulkSendController {

    private final BulkSendService bulkSendService;

    @PostMapping("/send-bulk")
    @Operation(summary = "Démarrer un envoi en masse (WhatsApp) — 202 + batch à suivre")
    public ResponseEntity<BulkSendBatchResponse> startBulkSend(
            @PathVariable Long weddingId,
            @Valid @RequestBody BulkSendRequest request) {
        BulkSendBatchResponse batch = bulkSendService.startBulkSend(weddingId, request);
        return ResponseEntity.accepted().body(batch);
    }

    @GetMapping("/send-bulk/{batchId}")
    @Operation(summary = "Progression d'un envoi en masse (compteurs envoyés/échecs/ignorés)")
    public ResponseEntity<BulkSendBatchResponse> getBatch(@PathVariable Long weddingId,
                                                          @PathVariable Long batchId) {
        return ResponseEntity.ok(bulkSendService.getBatch(weddingId, batchId));
    }

    @GetMapping("/send-bulk/{batchId}/logs")
    @Operation(summary = "Journal paginé d'un envoi en masse (compteurs envoyés/échecs/ignorés)")
    public ResponseEntity<PageResponse<NotificationLogResponse>> getLogs(@PathVariable Long weddingId,
                                                                       @PathVariable Long batchId,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(bulkSendService.getLogs(weddingId, batchId, page, size));
    }
}
