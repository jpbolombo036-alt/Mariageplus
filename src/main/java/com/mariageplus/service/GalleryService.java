package com.mariageplus.service;

import com.mariageplus.dto.gallery.*;
import com.mariageplus.entity.*;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.*;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GalleryService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final GalleryRepository galleryRepository;
    private final GalleryPhotoRepository photoRepository;
    private final GuestCategoryRepository categoryRepository;
    private final GuestRepository guestRepository;
    private final EventService eventService;
    private final InvitationService invitationService;
    private final StorageService storageService;
    private final SecurityUtils securityUtils;

    @Transactional
    public GalleryResponse getOrCreate(Long eventId) {
        Event event = eventService.loadInOrgScope(eventId);
        securityUtils.assertPermission("EVENT_VIEW");
        Gallery gallery = galleryRepository.findByEventId(eventId).orElseGet(() -> galleryRepository.save(
                Gallery.builder().eventId(eventId).title("Galerie de " + event.getName()).enabled(true).build()));
        return toResponse(gallery, "/api/events/" + eventId + "/gallery/photos/");
    }

    @Transactional
    public GalleryResponse update(Long eventId, GallerySettingsRequest request) {
        eventService.loadInOrgScope(eventId);
        securityUtils.assertPermission("EVENT_UPDATE");
        Gallery gallery = loadGallery(eventId);
        if (request.getTitle() != null && !request.getTitle().isBlank()) gallery.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) gallery.setDescription(request.getDescription());
        if (request.getEnabled() != null) gallery.setEnabled(request.getEnabled());
        return toResponse(galleryRepository.save(gallery), "/api/events/" + eventId + "/gallery/photos/");
    }

    @Transactional
    public GalleryPhotoResponse upload(Long eventId, MultipartFile file, Long guestCategoryId, String caption) {
        eventService.loadInOrgScope(eventId);
        securityUtils.assertPermission("EVENT_UPDATE");
        Gallery gallery = galleryRepository.findByEventId(eventId)
                .orElseGet(() -> galleryRepository.save(Gallery.builder().eventId(eventId).title("Galerie").build()));
        if (guestCategoryId != null && !categoryRepository.existsByIdAndWeddingId(guestCategoryId, eventId)) {
            throw new ResourceNotFoundException("Catégorie introuvable pour cet événement");
        }
        byte[] bytes;
        try { bytes = file == null ? null : file.getBytes(); }
        catch (IOException ex) { throw new IllegalArgumentException("Image illisible"); }
        String contentType = detectType(bytes);
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Image requise");
        if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Image trop volumineuse (max 10 Mo)");
        if (contentType == null) throw new IllegalArgumentException("Format image invalide");
        int order = photoRepository.findByGalleryIdOrderByDisplayOrderAscIdAsc(gallery.getId()).size();
        GalleryPhoto photo = GalleryPhoto.builder().galleryId(gallery.getId()).guestCategoryId(guestCategoryId)
                .contentType(contentType).originalFilename(file.getOriginalFilename())
                .caption(caption).displayOrder(order).build();
        if (storageService.isEnabled()) {
            String key = "galleries/" + eventId + "/" + System.nanoTime() + extension(contentType);
            storageService.upload(key, bytes, contentType);
            photo.setStorageKey(key);
        } else photo.setImage(bytes);
        return toPhotoResponse(photoRepository.save(photo), "/api/events/" + eventId + "/gallery/photos/");
    }

    @Transactional
    public void delete(Long eventId, Long photoId) {
        eventService.loadInOrgScope(eventId);
        securityUtils.assertPermission("EVENT_UPDATE");
        GalleryPhoto photo = photoRepository.findByIdAndGalleryId(photoId, loadGallery(eventId).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));
        if (photo.getStorageKey() != null) storageService.delete(photo.getStorageKey());
        photo.softDelete();
        photoRepository.save(photo);
    }

    @Transactional(readOnly = true)
    public GalleryResponse publicGallery(String publicToken) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));
        Gallery gallery = galleryRepository.findByEventId(invitation.getWeddingId())
                .filter(Gallery::isEnabled).orElseThrow(() -> new ResourceNotFoundException("Galerie indisponible"));
        List<GalleryPhotoResponse> photos = photoRepository.findByGalleryIdOrderByDisplayOrderAscIdAsc(gallery.getId()).stream()
                .filter(p -> p.getGuestCategoryId() == null || Objects.equals(p.getGuestCategoryId(), guest.getCategoryId()))
                .map(p -> toPhotoResponse(p, "/api/public/invitations/" + publicToken + "/gallery/photos/"))
                .toList();
        return GalleryResponse.builder().id(gallery.getId()).eventId(gallery.getEventId()).title(gallery.getTitle())
                .description(gallery.getDescription()).enabled(gallery.isEnabled()).photos(photos).build();
    }

    @Transactional(readOnly = true)
    public ImageData publicImage(String publicToken, Long photoId) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        Guest guest = guestRepository.findById(invitation.getGuestId()).orElseThrow(() -> new ResourceNotFoundException("Invitation introuvable"));
        Gallery gallery = galleryRepository.findByEventId(invitation.getWeddingId()).filter(Gallery::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Galerie indisponible"));
        GalleryPhoto photo = photoRepository.findByIdAndGalleryId(photoId, gallery.getId())
                .filter(p -> p.getGuestCategoryId() == null || Objects.equals(p.getGuestCategoryId(), guest.getCategoryId()))
                .orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));
        return image(photo);
    }

    @Transactional(readOnly = true)
    public ImageData adminImage(Long eventId, Long photoId) {
        eventService.loadInOrgScope(eventId); securityUtils.assertPermission("EVENT_VIEW");
        GalleryPhoto photo = photoRepository.findByIdAndGalleryId(photoId, loadGallery(eventId).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));
        return image(photo);
    }

    private ImageData image(GalleryPhoto photo) {
        byte[] bytes = photo.getStorageKey() != null ? storageService.download(photo.getStorageKey()) : photo.getImage();
        if (bytes == null) throw new ResourceNotFoundException("Image indisponible");
        return new ImageData(bytes, photo.getContentType());
    }

    private Gallery loadGallery(Long eventId) { return galleryRepository.findByEventId(eventId).orElseThrow(() -> new ResourceNotFoundException("Galerie introuvable")); }
    private GalleryResponse toResponse(Gallery g, String base) { return GalleryResponse.builder().id(g.getId()).eventId(g.getEventId()).title(g.getTitle()).description(g.getDescription()).enabled(g.isEnabled()).photos(photoRepository.findByGalleryIdOrderByDisplayOrderAscIdAsc(g.getId()).stream().map(p -> toPhotoResponse(p, base)).toList()).build(); }
    private GalleryPhotoResponse toPhotoResponse(GalleryPhoto p, String base) { long size = p.getImage() == null ? 0 : p.getImage().length; return GalleryPhotoResponse.builder().id(p.getId()).galleryId(p.getGalleryId()).guestCategoryId(p.getGuestCategoryId()).caption(p.getCaption()).displayOrder(p.getDisplayOrder()).contentType(p.getContentType()).size(size).imageUrl(base + p.getId() + "/image").build(); }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/gif" -> ".gif"; default -> ".webp"; }; }
    private String detectType(byte[] b) { if (b == null || b.length < 12) return null; if ((b[0]&255)==255 && (b[1]&255)==216) return "image/jpeg"; if ((b[0]&255)==137 && b[1]=='P' && b[2]=='N' && b[3]=='G') return "image/png"; if (b[0]=='G' && b[1]=='I' && b[2]=='F') return "image/gif"; if (b[0]=='R' && b[1]=='I' && b[2]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P') return "image/webp"; return null; }
    public record ImageData(byte[] bytes, String contentType) {}
}
