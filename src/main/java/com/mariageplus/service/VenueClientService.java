package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.venue.VenueBookingResponse;
import com.mariageplus.dto.venue.VenueClientRequest;
import com.mariageplus.dto.venue.VenueClientResponse;
import com.mariageplus.entity.VenueClient;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.VenueClientRepository;
import com.mariageplus.security.SecurityUtils;
import com.mariageplus.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Fiches clients des salles (module « Salles »). */
@Service
@RequiredArgsConstructor
public class VenueClientService {

    private final VenueClientRepository clientRepository;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** Indicatif utilisé pour les numéros locaux (0812…) : RDC par défaut. */
    @Value("${app.venue.default-country-code:243}")
    private String defaultCountryCode;

    @Transactional(readOnly = true)
    public PageResponse<VenueClientResponse> list(String query, int page, int size) {
        Long orgId = guard.requireEnabledOrganization("VENUE_CLIENT_MANAGE");
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("lastName").ascending().and(Sort.by("firstName").ascending()));
        Page<VenueClient> result;
        if (query == null || query.isBlank()) {
            result = clientRepository.findByOrganizationId(orgId, pageable);
        } else {
            String q = query.trim().toLowerCase(Locale.ROOT);
            String digits = q.replaceAll("[^0-9]", "");
            String phonePattern = digits.isEmpty() ? "\u0000" : "%" + digits + "%";
            result = clientRepository.search(orgId, "%" + q + "%", phonePattern, pageable);
        }
        return PageResponse.of(result.getContent().stream().map(c -> toResponse(c, null)).toList(), result);
    }

    @Transactional
    public VenueClientResponse create(VenueClientRequest request) {
        Long orgId = guard.requireEnabledOrganization("VENUE_CLIENT_MANAGE");
        VenueClient saved = createEntity(orgId, request);
        return toResponse(saved, null);
    }

    /** Création utilisée aussi par la réservation (« nouveau client » saisi dans le formulaire). */
    @Transactional
    public VenueClient createEntity(Long orgId, VenueClientRequest request) {
        VenueClient client = VenueClient.builder().organizationId(orgId).build();
        apply(client, request);
        VenueClient saved = clientRepository.save(client);
        auditService.record("VENUE_CLIENT_CREATE", saved.getId(), "VenueClient",
                securityUtils.getCurrentUserId(), orgId, "Nouveau client de salle : " + saved.getFullName());
        return saved;
    }

    @Transactional
    public VenueClientResponse update(Long clientId, VenueClientRequest request) {
        securityUtils.assertPermission("VENUE_CLIENT_MANAGE");
        VenueClient client = loadInScope(clientId);
        apply(client, request);
        VenueClient saved = clientRepository.save(client);
        auditService.record("VENUE_CLIENT_UPDATE", saved.getId(), "VenueClient",
                securityUtils.getCurrentUserId(), saved.getOrganizationId(), "Modification du client " + saved.getFullName());
        return toResponse(saved, null);
    }

    public VenueClient loadInScope(Long clientId) {
        VenueClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client introuvable : " + clientId));
        guard.assertAccess(client.getOrganizationId());
        return client;
    }

    public VenueClientResponse toResponse(VenueClient c, List<VenueBookingResponse> bookings) {
        return VenueClientResponse.builder()
                .id(c.getId())
                .firstName(c.getFirstName())
                .lastName(c.getLastName())
                .fullName(c.getFullName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .notes(c.getNotes())
                .createdAt(c.getCreatedAt())
                .bookings(bookings)
                .build();
    }

    private void apply(VenueClient client, VenueClientRequest r) {
        client.setFirstName(blankToNull(r.getFirstName()));
        client.setLastName(r.getLastName().trim());
        client.setEmail(r.getEmail() == null || r.getEmail().isBlank() ? null : r.getEmail().trim().toLowerCase(Locale.ROOT));
        client.setNotes(r.getNotes());
        if (r.getPhone() == null || r.getPhone().isBlank()) {
            client.setPhone(null);
        } else {
            String e164 = PhoneNormalizer.toE164(r.getPhone(), defaultCountryCode);
            if (e164 == null) {
                throw new IllegalArgumentException("Numéro de téléphone invalide : " + r.getPhone());
            }
            client.setPhone(e164);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
