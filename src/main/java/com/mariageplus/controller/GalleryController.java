package com.mariageplus.controller;

import com.mariageplus.dto.gallery.*;
import com.mariageplus.service.GalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/events/{eventId}/gallery")
@RequiredArgsConstructor
public class GalleryController {
    private final GalleryService service;

    @GetMapping public GalleryResponse get(@PathVariable Long eventId) { return service.getOrCreate(eventId); }
    @PutMapping public GalleryResponse update(@PathVariable Long eventId, @Valid @RequestBody GallerySettingsRequest request) { return service.update(eventId, request); }
    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GalleryPhotoResponse upload(@PathVariable Long eventId, @RequestParam("file") MultipartFile file, @RequestParam(required = false) Long guestCategoryId, @RequestParam(required = false) String caption) { return service.upload(eventId, file, guestCategoryId, caption); }
    @DeleteMapping("/photos/{photoId}") public ResponseEntity<Void> delete(@PathVariable Long eventId, @PathVariable Long photoId) { service.delete(eventId, photoId); return ResponseEntity.noContent().build(); }
    @GetMapping("/photos/{photoId}/image") public ResponseEntity<byte[]> image(@PathVariable Long eventId, @PathVariable Long photoId) { var i=service.adminImage(eventId, photoId); return ResponseEntity.ok().contentType(MediaType.parseMediaType(i.contentType())).body(i.bytes()); }
}
