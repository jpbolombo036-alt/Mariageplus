package com.mariageplus.controller;

import com.mariageplus.dto.gallery.GalleryResponse;
import com.mariageplus.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/invitations/{publicToken}/gallery")
@RequiredArgsConstructor
public class PublicGalleryController {
    private final GalleryService service;
    @GetMapping public GalleryResponse get(@PathVariable String publicToken) { return service.publicGallery(publicToken); }
    @GetMapping("/photos/{photoId}/image") public ResponseEntity<byte[]> image(@PathVariable String publicToken, @PathVariable Long photoId) { var i=service.publicImage(publicToken, photoId); return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, java.util.concurrent.TimeUnit.HOURS).cachePublic()).contentType(MediaType.parseMediaType(i.contentType())).body(i.bytes()); }
}
