package com.mariageplus.service;

import com.mariageplus.dto.event.CreateEventRequest;
import com.mariageplus.dto.event.EventResponse;
import com.mariageplus.dto.organization.OrganizationMemberRequest;
import com.mariageplus.dto.venue.CreateEventFromBookingRequest;
import com.mariageplus.dto.venue.VenueClientAccessRequest;
import com.mariageplus.dto.venue.VenueClientAccessResponse;
import com.mariageplus.entity.*;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.repository.EventRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.security.SecurityUtils;
import com.mariageplus.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Lien entre une réservation de salle et un événement MariagePlus :
 * création de l'événement pré-rempli, synchronisation de la date et de
 * l'annulation, et accès du client (GESTIONNAIRE_INVITES limité à son
 * événement). L'événement appartient à l'organisation de la salle, qui
 * le personnalise ; le client ne gère que ses invités.
 */
@Service
@RequiredArgsConstructor
public class VenueEventLinkService {

    private static final String CLIENT_ROLE = "GESTIONNAIRE_INVITES";

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final OrganizationMemberService organizationMemberService;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** Crée l'événement (brouillon) à partir de la réservation et retourne son identifiant. */
    @Transactional
    public Long createEvent(VenueBooking booking, Venue venue, VenueClient client, CreateEventFromBookingRequest request) {
        if (booking.getEventId() != null && eventRepository.findById(booking.getEventId()).isPresent()) {
            throw new ConflictException("Un événement est déjà lié à cette réservation (#" + booking.getEventId() + ")");
        }
        CreateEventFromBookingRequest r = request == null ? new CreateEventFromBookingRequest() : request;
        EventType type = booking.getEventType() == null ? EventType.OTHER : booking.getEventType();
        if (type == EventType.WEDDING && r.getWeddingDetails() == null) {
            throw new IllegalArgumentException("Pour un mariage, les noms des mariés (weddingDetails) sont requis");
        }

        CreateEventRequest event = new CreateEventRequest();
        event.setName(truncate(firstNonBlank(r.getName(), booking.getTitle(), "Événement de " + client.getFullName()), 150));
        event.setType(type);
        if (type == EventType.WEDDING) {
            event.setWeddingDetails(r.getWeddingDetails());
        }
        applySchedule(event, booking);
        event.setVenueName(truncate(venue.getName(), 200));
        event.setVenueAddress(truncate(venue.getAddress(), 255));
        event.setCity(truncate(venue.getCity(), 100));
        event.setCommune(truncate(venue.getCommune(), 100));
        event.setCountry(truncate(venue.getCountry(), 100));
        event.setLatitude(venue.getLatitude());
        event.setLongitude(venue.getLongitude());
        // Vérifie EVENT_CREATE et le réglage « création d'événements » de l'organisation.
        EventResponse created = eventService.create(event);
        return created.getId();
    }

    /** Reporte un changement de créneau ou de salle sur l'événement lié. */
    @Transactional
    public void syncSchedule(VenueBooking booking, Venue venue) {
        linkedEvent(booking).ifPresent(event -> {
            event.setEventDate(booking.getStartAt().toLocalDate());
            event.setStartTime(booking.getStartAt().toLocalTime());
            event.setEndTime(sameDayEnd(booking));
            event.setVenueName(truncate(venue.getName(), 200));
            event.setVenueAddress(truncate(venue.getAddress(), 255));
            event.setCity(truncate(venue.getCity(), 100));
            event.setCommune(truncate(venue.getCommune(), 100));
            event.setLatitude(venue.getLatitude());
            event.setLongitude(venue.getLongitude());
            eventRepository.save(event);
            auditService.record("EVENT_UPDATE", event.getId(), "Event", securityUtils.getCurrentUserId(),
                    event.getOrganizationId(), "Date / lieu synchronisés depuis la réservation de salle #" + booking.getId());
        });
    }

    /** Annule l'événement lié quand la réservation est annulée (si son statut le permet). */
    @Transactional
    public void cancelLinkedEvent(VenueBooking booking) {
        linkedEvent(booking).ifPresent(event -> {
            if (event.getStatus().canTransitionTo(EventStatus.CANCELLED)) {
                event.setStatus(EventStatus.CANCELLED);
                eventRepository.save(event);
                auditService.record("EVENT_STATUS", event.getId(), "Event", securityUtils.getCurrentUserId(),
                        event.getOrganizationId(), "Événement annulé avec la réservation de salle #" + booking.getId());
            }
        });
    }

    /**
     * Donne au client un compte GESTIONNAIRE_INVITES limité à l'événement de
     * sa réservation. Nouveau compte : mot de passe temporaire à changer à la
     * première connexion. Compte existant : simple rattachement.
     */
    @Transactional
    public VenueClientAccessResponse grantClientAccess(VenueBooking booking, VenueClient client, VenueClientAccessRequest request) {
        if (booking.getEventId() == null || linkedEvent(booking).isEmpty()) {
            throw new IllegalArgumentException("Créez d'abord l'événement MariagePlus de cette réservation");
        }
        String email = firstNonBlank(request.getEmail(), client.getEmail(), null);
        if (email == null) {
            throw new IllegalArgumentException("Un email est requis pour créer l'accès du client");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        boolean existing = userRepository.findByEmail(email).isPresent();
        String temporaryPassword = existing ? null : UserService.generateTemporaryPassword();

        OrganizationMemberRequest member = new OrganizationMemberRequest();
        member.setFirstName(firstNonBlank(request.getFirstName(), client.getFirstName(), "Client"));
        member.setLastName(firstNonBlank(request.getLastName(), client.getLastName(), "Client"));
        member.setEmail(email);
        member.setPhone(client.getPhone());
        member.setPassword(existing ? UserService.generateTemporaryPassword() : temporaryPassword);
        member.setRoleCode(CLIENT_ROLE);
        member.setWeddingId(booking.getEventId());
        organizationMemberService.addMember(booking.getOrganizationId(), member);

        User user = userRepository.findByEmail(email).orElseThrow();
        if (!existing) {
            user.setMustChangePassword(true);
            userRepository.save(user);
        }
        return VenueClientAccessResponse.builder()
                .userId(user.getId())
                .email(email)
                .eventId(booking.getEventId())
                .existingAccount(existing)
                .temporaryPassword(temporaryPassword)
                .whatsappNumber(PhoneNormalizer.toWhatsAppId(client.getPhone()))
                .build();
    }

    private Optional<Event> linkedEvent(VenueBooking booking) {
        return booking.getEventId() == null ? Optional.empty() : eventRepository.findById(booking.getEventId());
    }

    private void applySchedule(CreateEventRequest event, VenueBooking booking) {
        event.setEventDate(booking.getStartAt().toLocalDate());
        event.setStartTime(booking.getStartAt().toLocalTime());
        event.setEndTime(sameDayEnd(booking));
    }

    /** Heure de fin seulement si la réservation finit le même jour (sinon l'événement la laisse vide). */
    private LocalTime sameDayEnd(VenueBooking booking) {
        boolean sameDay = booking.getEndAt().toLocalDate().equals(booking.getStartAt().toLocalDate());
        LocalTime end = booking.getEndAt().toLocalTime();
        return sameDay && end.isAfter(booking.getStartAt().toLocalTime()) ? end : null;
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return fallback;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
