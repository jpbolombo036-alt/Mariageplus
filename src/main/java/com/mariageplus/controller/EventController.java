package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.event.CreateEventRequest;
import com.mariageplus.dto.event.EventResponse;
import com.mariageplus.dto.event.UpdateEventRequest;
import com.mariageplus.dto.event.UpdateEventStatusRequest;
import com.mariageplus.entity.EventType;
import com.mariageplus.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * Événements (nouvelle racine métier unifiée — Phase 1, coexistence avec
 * /api/weddings). Le type (mariage, collation, anniversaire...) porte les
 * champs spécifiques : seul WEDDING accepte {@code weddingDetails}.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Événements", description = "Gestion des événements (CRUD + statut, isolés par organisation)")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Liste paginée des événements (filtre optionnel par type, périmètre de l'utilisateur, global pour SUPER_ADMIN)")
    public ResponseEntity<PageResponse<EventResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) EventType type) {
        return ResponseEntity.ok(eventService.list(page, size, sortBy, sortDir, type));
    }

    @PostMapping
    @Operation(summary = "Créer un événement (weddingDetails requis si type=WEDDING, interdit sinon)")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Événement par ID (périmètre vérifié, inclut weddingDetails et sessions)")
    public ResponseEntity<EventResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un événement (périmètre vérifié)")
    public ResponseEntity<EventResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.update(id, request));
    }

    @PutMapping("/{id}/image")
    @Operation(summary = "Uploader la photo de couverture (JPEG, PNG, GIF, WebP — max 2 Mo)")
    public ResponseEntity<?> uploadImage(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file) {
        try {
            eventService.setImage(id, file.getBytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Impossible de lire le fichier"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    @Operation(summary = "Photo de couverture de l'événement")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        byte[] image = eventService.getImage(id);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectMediaType(image)))
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(image);
    }

    @DeleteMapping("/{id}/image")
    @Operation(summary = "Supprimer la photo de couverture")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        eventService.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/photos/{kind}")
    @Operation(summary = "Uploader une photo de la fiche mariage (kind = groom | bride | couple — max 2 Mo)")
    public ResponseEntity<?> uploadDetailPhoto(@PathVariable Long id,
                                               @PathVariable String kind,
                                               @RequestParam("file") MultipartFile file) {
        try {
            eventService.setDetailPhoto(id, kind, file.getBytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Impossible de lire le fichier"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photos/{kind}")
    @Operation(summary = "Photo de la fiche mariage (public : affichée sur la page d'invitation)")
    public ResponseEntity<byte[]> getDetailPhoto(@PathVariable Long id, @PathVariable String kind) {
        byte[] image = eventService.getDetailPhoto(id, kind);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectMediaType(image)))
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(image);
    }

    private String detectMediaType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return "image/gif";
        return "image/webp";
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un événement (transition validée, permission selon la cible)")
    public ResponseEntity<EventResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateEventStatusRequest request) {
        return ResponseEntity.ok(eventService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un événement (suppression logique)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
