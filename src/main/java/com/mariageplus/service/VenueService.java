package com.mariageplus.service;

import com.mariageplus.dto.venue.VenueRateRequest;
import com.mariageplus.dto.venue.VenueRateResponse;
import com.mariageplus.dto.venue.VenueRequest;
import com.mariageplus.dto.venue.VenueResponse;
import com.mariageplus.entity.Venue;
import com.mariageplus.entity.VenueBookingStatus;
import com.mariageplus.entity.VenueRate;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.VenueBookingRepository;
import com.mariageplus.repository.VenueRateRepository;
import com.mariageplus.repository.VenueRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/** Salles et tarifs d'une organisation (module « Salles »). */
@Service
@RequiredArgsConstructor
public class VenueService {

    private static final int IMAGE_MAX_BYTES = 2 * 1024 * 1024;

    private final VenueRepository venueRepository;
    private final VenueRateRepository rateRepository;
    private final VenueBookingRepository bookingRepository;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<VenueResponse> list() {
        Long orgId = guard.requireEnabledOrganization("VENUE_VIEW");
        return venueRepository.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse get(Long venueId) {
        securityUtils.assertPermission("VENUE_VIEW");
        return toResponse(loadInScope(venueId));
    }

    @Transactional
    public VenueResponse create(VenueRequest request) {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        Venue venue = Venue.builder().organizationId(orgId).active(true).build();
        apply(venue, request);
        Venue saved = venueRepository.save(venue);
        audit("VENUE_CREATE", saved.getId(), orgId, "Création de la salle '" + saved.getName() + "'");
        return toResponse(saved);
    }

    @Transactional
    public VenueResponse update(Long venueId, VenueRequest request) {
        securityUtils.assertPermission("VENUE_MANAGE");
        Venue venue = loadInScope(venueId);
        apply(venue, request);
        Venue saved = venueRepository.save(venue);
        audit("VENUE_UPDATE", saved.getId(), saved.getOrganizationId(), "Modification de la salle");
        return toResponse(saved);
    }

    /**
     * Suppression logique ; les réservations passées restent consultables.
     * Refusée tant que la salle a des réservations à venir (option ou confirmée) :
     * elles perdraient leur salle sans que personne ne s'en rende compte.
     */
    @Transactional
    public void delete(Long venueId) {
        securityUtils.assertPermission("VENUE_MANAGE");
        Venue venue = loadInScope(venueId);
        long upcoming = bookingRepository.countByVenueIdAndStatusInAndEndAtAfter(venue.getId(),
                EnumSet.of(VenueBookingStatus.DEMANDE, VenueBookingStatus.OPTION, VenueBookingStatus.CONFIRMEE),
                LocalDateTime.now());
        if (upcoming > 0) {
            throw new ConflictException("Impossible de supprimer « " + venue.getName() + " » : " + upcoming
                    + " réservation(s) à venir. Annulez-les ou déplacez-les d'abord, ou désactivez la salle.");
        }
        venue.softDelete();
        venueRepository.save(venue);
        audit("VENUE_DELETE", venueId, venue.getOrganizationId(), "Suppression de la salle '" + venue.getName() + "'");
    }

    /** Salles supprimées de l'organisation, pour pouvoir les restaurer. */
    @Transactional(readOnly = true)
    public List<VenueResponse> listDeleted() {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        return venueRepository.findDeletedByOrganizationId(orgId).stream().map(this::toResponse).toList();
    }

    /** Annule la suppression d'une salle de l'organisation. */
    @Transactional
    public VenueResponse restore(Long venueId) {
        Long orgId = guard.requireEnabledOrganization("VENUE_MANAGE");
        if (venueRepository.restore(venueId, orgId) == 0) {
            throw new ResourceNotFoundException("Salle supprimée introuvable : " + venueId);
        }
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Salle introuvable : " + venueId));
        audit("VENUE_RESTORE", venueId, orgId, "Restauration de la salle '" + venue.getName() + "'");
        return toResponse(venue);
    }

    // ---- Tarifs -----------------------------------------------------------

    @Transactional
    public VenueRateResponse addRate(Long venueId, VenueRateRequest request) {
        securityUtils.assertPermission("VENUE_MANAGE");
        Venue venue = loadInScope(venueId);
        VenueRate rate = VenueRate.builder()
                .venueId(venue.getId())
                .label(request.getLabel().trim())
                .price(VenueMoney.scale(request.getPrice()))
                .currency(request.getCurrency())
                .active(request.getActive() == null || request.getActive())
                .build();
        VenueRate saved = rateRepository.save(rate);
        audit("VENUE_RATE_CREATE", saved.getId(), venue.getOrganizationId(),
                "Tarif '" + saved.getLabel() + "' : " + VenueMoney.format(saved.getPrice(), saved.getCurrency()));
        return toRateResponse(saved);
    }

    @Transactional
    public VenueRateResponse updateRate(Long venueId, Long rateId, VenueRateRequest request) {
        securityUtils.assertPermission("VENUE_MANAGE");
        Venue venue = loadInScope(venueId);
        VenueRate rate = loadRate(venue.getId(), rateId);
        rate.setLabel(request.getLabel().trim());
        rate.setPrice(VenueMoney.scale(request.getPrice()));
        rate.setCurrency(request.getCurrency());
        if (request.getActive() != null) {
            rate.setActive(request.getActive());
        }
        VenueRate saved = rateRepository.save(rate);
        audit("VENUE_RATE_UPDATE", saved.getId(), venue.getOrganizationId(), "Modification du tarif '" + saved.getLabel() + "'");
        return toRateResponse(saved);
    }

    @Transactional
    public void deleteRate(Long venueId, Long rateId) {
        securityUtils.assertPermission("VENUE_MANAGE");
        Venue venue = loadInScope(venueId);
        VenueRate rate = loadRate(venue.getId(), rateId);
        rate.softDelete();
        rateRepository.save(rate);
        audit("VENUE_RATE_DELETE", rateId, venue.getOrganizationId(), "Suppression du tarif '" + rate.getLabel() + "'");
    }

    // ---- Photo ------------------------------------------------------------

    @Transactional
    public void setImage(Long venueId, byte[] image) {
        securityUtils.assertPermission("VENUE_MANAGE");
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Fichier image vide ou manquant");
        }
        if (image.length > IMAGE_MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 2 Mo)");
        }
        String contentType = ImageFormats.contentType(image);
        if (contentType == null) {
            throw new IllegalArgumentException("Format d'image non supporté (JPEG, PNG, GIF ou WebP attendu)");
        }
        Venue venue = loadInScope(venueId);
        if (storageService.isEnabled()) {
            if (venue.getImageKey() != null && !venue.getImageKey().isBlank()) {
                storageService.delete(venue.getImageKey());
            }
            String key = "venues/" + venue.getOrganizationId() + "/" + venueId + "-" + System.currentTimeMillis()
                    + ImageFormats.extension(contentType);
            storageService.upload(key, image, contentType);
            venue.setImageKey(key);
            venue.setImage(null);
        } else {
            venue.setImage(image);
        }
        venueRepository.save(venue);
        audit("VENUE_UPDATE", venueId, venue.getOrganizationId(), "Mise à jour de la photo de la salle");
    }

    /** Photo publique d'une salle (S3 d'abord, base en fallback) ; null si aucune. */
    @Transactional(readOnly = true)
    public byte[] getPublicImage(Long venueId) {
        Venue venue = venueRepository.findById(venueId).orElse(null);
        if (venue == null) {
            return null;
        }
        if (venue.getImageKey() != null && !venue.getImageKey().isBlank()) {
            byte[] fromS3 = storageService.download(venue.getImageKey());
            if (fromS3 != null) {
                return fromS3;
            }
        }
        return venue.getImage() == null || venue.getImage().length == 0 ? null : venue.getImage();
    }

    @Transactional
    public void deleteImage(Long venueId) {
        securityUtils.assertPermission("VENUE_MANAGE");
        Venue venue = loadInScope(venueId);
        if (venue.getImageKey() != null && !venue.getImageKey().isBlank()) {
            storageService.delete(venue.getImageKey());
        }
        venue.setImageKey(null);
        venue.setImage(null);
        venueRepository.save(venue);
    }

    // ---- Internes -----------------------------------------------------------

    /** Charge une salle de l'organisation de l'utilisateur (module activé). */
    public Venue loadInScope(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Salle introuvable : " + venueId));
        guard.assertAccess(venue.getOrganizationId());
        return venue;
    }

    public VenueRate loadRate(Long venueId, Long rateId) {
        return rateRepository.findByIdAndVenueId(rateId, venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarif introuvable pour cette salle : " + rateId));
    }

    private void apply(Venue venue, VenueRequest r) {
        venue.setName(r.getName().trim());
        if (r.getDescription() != null) venue.setDescription(r.getDescription());
        if (r.getAddress() != null) venue.setAddress(r.getAddress());
        if (r.getCommune() != null) venue.setCommune(r.getCommune());
        if (r.getCity() != null) venue.setCity(r.getCity());
        if (r.getCountry() != null) venue.setCountry(r.getCountry());
        if (r.getLatitude() != null) venue.setLatitude(r.getLatitude());
        if (r.getLongitude() != null) venue.setLongitude(r.getLongitude());
        if (r.getCapacitySeated() != null) venue.setCapacitySeated(r.getCapacitySeated());
        if (r.getCapacityStanding() != null) venue.setCapacityStanding(r.getCapacityStanding());
        if (r.getAmenities() != null) venue.setAmenities(r.getAmenities());
        if (r.getActive() != null) venue.setActive(r.getActive());
    }

    private VenueResponse toResponse(Venue v) {
        boolean hasImage = (v.getImageKey() != null && !v.getImageKey().isBlank())
                || (v.getImage() != null && v.getImage().length > 0);
        return VenueResponse.builder()
                .id(v.getId())
                .name(v.getName())
                .description(v.getDescription())
                .address(v.getAddress())
                .commune(v.getCommune())
                .city(v.getCity())
                .country(v.getCountry())
                .latitude(v.getLatitude())
                .longitude(v.getLongitude())
                .capacitySeated(v.getCapacitySeated())
                .capacityStanding(v.getCapacityStanding())
                .amenities(v.getAmenities())
                .active(v.isActive())
                .imageUrl(hasImage ? "/api/venues/" + v.getId() + "/image" : null)
                .rates(rateRepository.findByVenueIdOrderByLabelAsc(v.getId()).stream().map(this::toRateResponse).toList())
                .build();
    }

    private VenueRateResponse toRateResponse(VenueRate r) {
        return VenueRateResponse.builder()
                .id(r.getId())
                .venueId(r.getVenueId())
                .label(r.getLabel())
                .price(r.getPrice())
                .currency(r.getCurrency())
                .active(r.isActive())
                .build();
    }

    private void audit(String action, Long entityId, Long orgId, String details) {
        auditService.record(action, entityId, "Venue", securityUtils.getCurrentUserId(), orgId, details);
    }
}
