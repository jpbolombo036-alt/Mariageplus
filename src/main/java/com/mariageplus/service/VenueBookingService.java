package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.venue.*;
import com.mariageplus.entity.*;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.*;
import com.mariageplus.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Réservations de salle : création, calendrier, statuts, prestations et
 * paiements. Toute écriture qui touche au créneau d'une salle se fait sous
 * verrou de la ligne {@code venues} (voir {@link VenueRepository#findByIdForUpdate}),
 * ce qui empêche deux réservations simultanées du même créneau.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VenueBookingService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VenueBookingRepository bookingRepository;
    private final VenueBookingExtraRepository extraRepository;
    private final VenueBookingPaymentRepository paymentRepository;
    private final VenueRepository venueRepository;
    private final VenueClientRepository clientRepository;
    private final VenueService venueService;
    private final VenueClientService clientService;
    private final VenueEventLinkService eventLinkService;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** Durée d'une OPTION sans acompte avant libération automatique de la date. */
    @Value("${app.venue.option-days:7}")
    private int defaultOptionDays;

    // ======================================================================
    // Lecture
    // ======================================================================

    @Transactional(readOnly = true)
    public PageResponse<VenueBookingResponse> list(Long venueId, Long clientId, VenueBookingStatus status,
                                                   LocalDate from, LocalDate to, int page, int size) {
        Long orgId = guard.requireEnabledOrganization("BOOKING_VIEW");
        Specification<VenueBooking> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            if (venueId != null) p.add(cb.equal(root.get("venueId"), venueId));
            if (clientId != null) p.add(cb.equal(root.get("clientId"), clientId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (from != null) p.add(cb.greaterThan(root.get("endAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(root.get("startAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by("startAt").ascending());
        Page<VenueBooking> result = bookingRepository.findAll(spec, pageable);
        return PageResponse.of(toSummaries(result.getContent()), result);
    }

    @Transactional(readOnly = true)
    public VenueBookingResponse get(Long bookingId) {
        securityUtils.assertPermission("BOOKING_VIEW");
        return toDetail(loadInScope(bookingId));
    }

    /** Réservations d'une salle qui touchent le mois demandé (format AAAA-MM). */
    @Transactional(readOnly = true)
    public List<VenueBookingResponse> calendar(Long venueId, YearMonth month) {
        securityUtils.assertPermission("BOOKING_VIEW");
        Venue venue = venueService.loadInScope(venueId);
        YearMonth m = month == null ? YearMonth.now() : month;
        List<VenueBooking> bookings = bookingRepository.findInPeriod(venue.getId(),
                m.atDay(1).atStartOfDay(), m.plusMonths(1).atDay(1).atStartOfDay());
        return toSummaries(bookings.stream().filter(b -> b.getStatus() != VenueBookingStatus.ANNULEE).toList());
    }

    @Transactional(readOnly = true)
    public VenueClientResponse clientDetail(Long clientId) {
        securityUtils.assertPermission("VENUE_CLIENT_MANAGE");
        VenueClient client = clientService.loadInScope(clientId);
        return clientService.toResponse(client, toSummaries(bookingRepository.findByClientIdOrderByStartAtDesc(clientId)));
    }

    // ======================================================================
    // Création / modification
    // ======================================================================

    @Transactional
    public VenueBookingResponse create(VenueBookingRequest request) {
        Long orgId = guard.requireEnabledOrganization("BOOKING_MANAGE");
        if (request.getVenueId() == null) {
            throw new IllegalArgumentException("La salle (venueId) est requise");
        }
        if (request.getStartAt() == null || request.getEndAt() == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont requises");
        }
        VenueBookingStatus status = request.getStatus() == null ? VenueBookingStatus.OPTION : request.getStatus();
        if (status != VenueBookingStatus.OPTION && status != VenueBookingStatus.CONFIRMEE
                && status != VenueBookingStatus.DEMANDE) {
            throw new IllegalArgumentException("Une réservation se crée en DEMANDE, OPTION ou CONFIRMEE");
        }

        // Verrou de la salle AVANT le contrôle de chevauchement.
        Venue venue = lockVenue(request.getVenueId());
        if (!venue.isActive()) {
            throw new IllegalArgumentException("Cette salle est désactivée");
        }
        VenueClient client = resolveClient(orgId, request);

        VenueBooking booking = VenueBooking.builder()
                .organizationId(orgId)
                .venueId(venue.getId())
                .clientId(client.getId())
                .title(blankToNull(request.getTitle()))
                .eventType(request.getEventType())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .guestCount(request.getGuestCount())
                .status(status)
                .notes(request.getNotes())
                .createdBy(securityUtils.getCurrentUserId())
                .build();
        applyPricing(booking, venue, request, true);
        validatePeriod(booking);
        if (status.isBlocking()) {
            assertNoOverlap(booking);
        }
        if (status == VenueBookingStatus.OPTION) {
            int days = request.getOptionDays() != null ? request.getOptionDays() : defaultOptionDays;
            booking.setOptionExpiresAt(LocalDateTime.now().plusDays(days));
        }
        booking.setTotalAmount(computeTotal(booking, List.of()));
        VenueBooking saved = bookingRepository.save(booking);
        audit("BOOKING_CREATE", saved, "Réservation " + reference(saved) + " (" + saved.getStatus() + ") : "
                + venue.getName() + " du " + DATE_TIME.format(saved.getStartAt()) + " au " + DATE_TIME.format(saved.getEndAt())
                + " pour " + client.getFullName());
        return toDetail(saved);
    }

    @Transactional
    public VenueBookingResponse update(Long bookingId, VenueBookingRequest request) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        assertEditable(booking);
        if (request.getStatus() != null && request.getStatus() != booking.getStatus()) {
            throw new IllegalArgumentException("Le statut se change avec /confirm, /complete ou /cancel");
        }
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        Long oldVenueId = booking.getVenueId();

        Long targetVenueId = request.getVenueId() != null ? request.getVenueId() : booking.getVenueId();
        // Seule la salle d'arrivée est verrouillée : quitter un créneau ne crée pas de conflit.
        Venue venue = lockVenue(targetVenueId);
        booking.setVenueId(venue.getId());

        if (request.getClientId() != null || request.getClient() != null) {
            booking.setClientId(resolveClient(booking.getOrganizationId(), request).getId());
        }
        if (request.getTitle() != null) booking.setTitle(blankToNull(request.getTitle()));
        if (request.getEventType() != null) booking.setEventType(request.getEventType());
        if (request.getStartAt() != null) booking.setStartAt(request.getStartAt());
        if (request.getEndAt() != null) booking.setEndAt(request.getEndAt());
        if (request.getGuestCount() != null) booking.setGuestCount(request.getGuestCount());
        if (request.getNotes() != null) booking.setNotes(request.getNotes());
        if (request.getCurrency() != null && request.getCurrency() != booking.getCurrency()
                && paymentRepository.existsByBookingId(booking.getId())) {
            throw new IllegalArgumentException(
                    "Impossible de changer la devise : des paiements sont déjà enregistrés sur cette réservation");
        }
        applyPricing(booking, venue, request, false);
        validatePeriod(booking);
        if (booking.getStatus().isBlocking()) {
            assertNoOverlap(booking);
        }
        List<VenueBookingExtra> extras = extraRepository.findByBookingIdOrderByIdAsc(booking.getId());
        booking.setTotalAmount(computeTotal(booking, extras));
        assertNotOverpaid(booking);
        VenueBooking saved = bookingRepository.save(booking);

        boolean moved = !oldStart.equals(saved.getStartAt()) || !oldEnd.equals(saved.getEndAt())
                || !oldVenueId.equals(saved.getVenueId());
        if (moved) {
            eventLinkService.syncSchedule(saved, venue);
        }
        audit("BOOKING_UPDATE", saved, "Modification de la réservation " + reference(saved)
                + (moved ? " (nouveau créneau : " + DATE_TIME.format(saved.getStartAt()) + " → "
                + DATE_TIME.format(saved.getEndAt()) + ")" : ""));
        return toDetail(saved);
    }

    @Transactional
    public VenueBookingResponse confirm(Long bookingId) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        lockVenue(booking.getVenueId());
        transition(booking, VenueBookingStatus.CONFIRMEE);
        // DEMANDE → CONFIRMEE : le créneau devient bloquant, on revérifie.
        assertNoOverlap(booking);
        booking.setOptionExpiresAt(null);
        VenueBooking saved = bookingRepository.save(booking);
        audit("BOOKING_CONFIRM", saved, "Réservation " + reference(saved) + " confirmée");
        return toDetail(saved);
    }

    @Transactional
    public VenueBookingResponse complete(Long bookingId) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        transition(booking, VenueBookingStatus.REALISEE);
        VenueBooking saved = bookingRepository.save(booking);
        audit("BOOKING_COMPLETE", saved, "Réservation " + reference(saved) + " réalisée");
        return toDetail(saved);
    }

    @Transactional
    public VenueBookingResponse cancel(Long bookingId, String reason) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        cancelInternal(booking, blankToNull(reason));
        return toDetail(booking);
    }

    /**
     * Libère les OPTIONS échues sans aucun paiement. Idempotent : sans risque
     * si plusieurs instances l'exécutent en même temps.
     */
    @Scheduled(cron = "${app.venue.option-expiry-cron:0 30 * * * *}")
    @Transactional
    public int expireOptions() {
        int count = 0;
        for (VenueBooking booking : bookingRepository.findByStatusAndOptionExpiresAtBefore(
                VenueBookingStatus.OPTION, LocalDateTime.now())) {
            if (paymentRepository.existsByBookingId(booking.getId())) {
                continue; // un acompte a été versé : l'option reste bloquée
            }
            booking.setStatus(VenueBookingStatus.ANNULEE);
            booking.setCancelReason("Option expirée sans acompte");
            bookingRepository.save(booking);
            eventLinkService.cancelLinkedEvent(booking);
            auditService.record("BOOKING_OPTION_EXPIRED", booking.getId(), "VenueBooking", null,
                    booking.getOrganizationId(), "Option " + reference(booking) + " expirée sans acompte : date libérée");
            count++;
        }
        if (count > 0) {
            log.info("Module Salles : {} option(s) expirée(s) libérée(s)", count);
        }
        return count;
    }

    // ======================================================================
    // Prestations
    // ======================================================================

    @Transactional
    public VenueBookingResponse addExtra(Long bookingId, VenueBookingExtraRequest request) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        assertEditable(booking);
        VenueBookingExtra extra = VenueBookingExtra.builder()
                .bookingId(booking.getId())
                .label(request.getLabel().trim())
                .quantity(request.getQuantity() == null ? 1 : request.getQuantity())
                .unitPrice(VenueMoney.scale(request.getUnitPrice()))
                .build();
        extraRepository.save(extra);
        booking.setTotalAmount(computeTotal(booking, extraRepository.findByBookingIdOrderByIdAsc(booking.getId())));
        bookingRepository.save(booking);
        audit("BOOKING_EXTRA_ADD", booking, "Prestation '" + extra.getLabel() + "' x" + extra.getQuantity()
                + " ajoutée à " + reference(booking));
        return toDetail(booking);
    }

    @Transactional
    public VenueBookingResponse removeExtra(Long bookingId, Long extraId) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        assertEditable(booking);
        VenueBookingExtra extra = extraRepository.findByIdAndBookingId(extraId, booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable : " + extraId));
        extra.softDelete();
        extraRepository.save(extra);
        List<VenueBookingExtra> remaining = extraRepository.findByBookingIdOrderByIdAsc(booking.getId()).stream()
                .filter(e -> !e.getId().equals(extraId)).toList();
        booking.setTotalAmount(computeTotal(booking, remaining));
        assertNotOverpaid(booking);
        bookingRepository.save(booking);
        audit("BOOKING_EXTRA_REMOVE", booking, "Prestation '" + extra.getLabel() + "' retirée de " + reference(booking));
        return toDetail(booking);
    }

    // ======================================================================
    // Paiements
    // ======================================================================

    @Transactional
    public VenuePaymentResponse recordPayment(Long bookingId, VenuePaymentRequest request) {
        securityUtils.assertPermission("BOOKING_PAYMENT_RECORD");
        VenueBooking booking = loadInScope(bookingId);
        if (booking.getStatus() == VenueBookingStatus.ANNULEE) {
            throw new IllegalArgumentException("Impossible d'encaisser sur une réservation annulée");
        }
        VenueCurrency currency = request.getCurrency() == null ? booking.getCurrency() : request.getCurrency();
        BigDecimal amount = VenueMoney.scale(request.getAmount());
        BigDecimal rate = currency == booking.getCurrency() ? null : request.getExchangeRate();
        BigDecimal converted = VenueMoney.convert(amount, currency, booking.getCurrency(), rate);
        BigDecimal balance = booking.getTotalAmount().subtract(paidAmount(booking.getId()));
        if (converted.compareTo(balance) > 0) {
            throw new IllegalArgumentException("Le paiement (" + VenueMoney.format(converted, booking.getCurrency())
                    + ") dépasse le reste à payer (" + VenueMoney.format(balance, booking.getCurrency()) + ")");
        }
        VenueBookingPayment payment = VenueBookingPayment.builder()
                .bookingId(booking.getId())
                .amount(amount)
                .currency(currency)
                .exchangeRate(rate)
                .amountInBookingCurrency(converted)
                .method(request.getMethod())
                .reference(blankToNull(request.getReference()))
                .paidAt(request.getPaidAt() == null ? LocalDateTime.now() : request.getPaidAt())
                .note(request.getNote())
                .recordedBy(securityUtils.getCurrentUserId())
                .build();
        VenueBookingPayment saved = paymentRepository.save(payment);
        audit("BOOKING_PAYMENT", booking, "Paiement " + receiptNumber(saved) + " de " + VenueMoney.format(amount, currency)
                + " (" + saved.getMethod() + ") sur " + reference(booking));
        return toPaymentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VenuePaymentResponse> listPayments(Long bookingId) {
        securityUtils.assertPermission("BOOKING_VIEW");
        VenueBooking booking = loadInScope(bookingId);
        return paymentRepository.findByBookingIdOrderByPaidAtAscIdAsc(booking.getId()).stream()
                .map(this::toPaymentResponse).toList();
    }

    /** Annule un paiement saisi par erreur (suppression logique, tracée dans l'audit). */
    @Transactional
    public void deletePayment(Long bookingId, Long paymentId) {
        securityUtils.assertPermission("BOOKING_PAYMENT_RECORD");
        VenueBooking booking = loadInScope(bookingId);
        VenueBookingPayment payment = paymentRepository.findByIdAndBookingId(paymentId, booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable : " + paymentId));
        payment.softDelete();
        paymentRepository.save(payment);
        audit("BOOKING_PAYMENT_DELETE", booking, "Paiement " + receiptNumber(payment) + " de "
                + VenueMoney.format(payment.getAmount(), payment.getCurrency()) + " annulé sur " + reference(booking));
    }

    // ======================================================================
    // Lien avec MariagePlus
    // ======================================================================

    @Transactional
    public VenueBookingResponse createEvent(Long bookingId, CreateEventFromBookingRequest request) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        if (booking.getStatus() != VenueBookingStatus.CONFIRMEE) {
            throw new IllegalArgumentException("L'événement se crée depuis une réservation CONFIRMEE");
        }
        Venue venue = venueRepository.findById(booking.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Salle introuvable : " + booking.getVenueId()));
        VenueClient client = clientService.loadInScope(booking.getClientId());
        Long eventId = eventLinkService.createEvent(booking, venue, client, request);
        booking.setEventId(eventId);
        VenueBooking saved = bookingRepository.save(booking);
        audit("BOOKING_EVENT_CREATE", saved, "Événement #" + eventId + " créé depuis la réservation " + reference(saved));
        return toDetail(saved);
    }

    @Transactional
    public VenueClientAccessResponse grantClientAccess(Long bookingId, VenueClientAccessRequest request) {
        securityUtils.assertPermission("BOOKING_MANAGE");
        VenueBooking booking = loadInScope(bookingId);
        if (booking.getStatus() == VenueBookingStatus.ANNULEE) {
            throw new IllegalArgumentException("Réservation annulée");
        }
        VenueClient client = clientService.loadInScope(booking.getClientId());
        VenueClientAccessResponse response = eventLinkService.grantClientAccess(booking, client,
                request == null ? new VenueClientAccessRequest() : request);
        audit("BOOKING_CLIENT_ACCESS", booking, "Accès invités donné à " + response.getEmail()
                + " pour l'événement #" + booking.getEventId() + (response.isExistingAccount() ? " (compte existant)" : ""));
        return response;
    }

    // ======================================================================
    // Internes partagés (rapports, documents)
    // ======================================================================

    public VenueBooking loadInScope(Long bookingId) {
        VenueBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation introuvable : " + bookingId));
        guard.assertAccess(booking.getOrganizationId());
        return booking;
    }

    public BigDecimal paidAmount(Long bookingId) {
        return paymentRepository.sumPaidByBookingIds(List.of(bookingId)).stream()
                .map(VenueBookingPaymentRepository.PaidTotal::getPaid)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String reference(VenueBooking booking) {
        return "RES-" + String.format("%05d", booking.getId() == null ? 0 : booking.getId());
    }

    public static String receiptNumber(VenueBookingPayment payment) {
        return "REC-" + String.format("%05d", payment.getId() == null ? 0 : payment.getId());
    }

    /** Résumés (sans prestations ni paiements) avec montants payés et noms chargés en lot. */
    public List<VenueBookingResponse> toSummaries(List<VenueBooking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = bookings.stream().map(VenueBooking::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> paid = paymentRepository.sumPaidByBookingIds(ids).stream()
                .collect(Collectors.toMap(VenueBookingPaymentRepository.PaidTotal::getBookingId,
                        t -> t.getPaid() == null ? BigDecimal.ZERO : t.getPaid()));
        Map<Long, Venue> venues = venueRepository.findAllById(
                        bookings.stream().map(VenueBooking::getVenueId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        Map<Long, VenueClient> clients = clientRepository.findByIdIn(
                        bookings.stream().map(VenueBooking::getClientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueClient::getId, Function.identity()));
        return bookings.stream()
                .map(b -> baseResponse(b, venues.get(b.getVenueId()), clients.get(b.getClientId()),
                        paid.getOrDefault(b.getId(), BigDecimal.ZERO), null))
                .toList();
    }

    public VenueBookingResponse toDetail(VenueBooking booking) {
        Venue venue = venueRepository.findById(booking.getVenueId()).orElse(null);
        VenueClient client = clientRepository.findById(booking.getClientId()).orElse(null);
        List<VenueBookingExtra> extras = extraRepository.findByBookingIdOrderByIdAsc(booking.getId());
        List<VenueBookingPayment> payments = paymentRepository.findByBookingIdOrderByPaidAtAscIdAsc(booking.getId());
        BigDecimal paid = payments.stream().map(VenueBookingPayment::getAmountInBookingCurrency)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        VenueBookingResponse response = baseResponse(booking, venue, client, paid, extras);
        response.setExtras(extras.stream().map(e -> VenueBookingExtraResponse.builder()
                .id(e.getId()).label(e.getLabel()).quantity(e.getQuantity())
                .unitPrice(e.getUnitPrice()).lineTotal(VenueMoney.scale(e.getLineTotal())).build()).toList());
        response.setPayments(payments.stream().map(this::toPaymentResponse).toList());
        return response;
    }

    // ======================================================================
    // Privés
    // ======================================================================

    private Venue lockVenue(Long venueId) {
        Venue venue = venueRepository.findByIdForUpdate(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Salle introuvable : " + venueId));
        guard.assertAccess(venue.getOrganizationId());
        return venue;
    }

    private VenueClient resolveClient(Long orgId, VenueBookingRequest request) {
        if (request.getClientId() != null) {
            VenueClient client = clientService.loadInScope(request.getClientId());
            if (!client.getOrganizationId().equals(orgId)) {
                throw new ResourceNotFoundException("Client introuvable : " + request.getClientId());
            }
            return client;
        }
        if (request.getClient() != null) {
            securityUtils.assertPermission("VENUE_CLIENT_MANAGE");
            if (request.getClient().getLastName() == null || request.getClient().getLastName().isBlank()) {
                throw new IllegalArgumentException("Le nom du client est requis");
            }
            return clientService.createEntity(orgId, request.getClient());
        }
        throw new IllegalArgumentException("Un client est requis (clientId ou client)");
    }

    /**
     * Prix : tarif choisi (prix et devise par défaut) ou prix saisi. À la
     * création, la devise vaut celle du tarif, sinon USD.
     */
    private void applyPricing(VenueBooking booking, Venue venue, VenueBookingRequest request, boolean creating) {
        VenueRate rate = null;
        if (request.getRateId() != null) {
            rate = venueService.loadRate(venue.getId(), request.getRateId());
            booking.setRateId(rate.getId());
        } else if (!creating && booking.getRateId() != null && !booking.getVenueId().equals(venue.getId())) {
            booking.setRateId(null);
        }
        if (request.getCurrency() != null) {
            booking.setCurrency(request.getCurrency());
        } else if (creating) {
            booking.setCurrency(rate != null ? rate.getCurrency() : VenueCurrency.USD);
        }
        if (request.getBasePrice() != null) {
            booking.setBasePrice(VenueMoney.scale(request.getBasePrice()));
        } else if (rate != null) {
            if (rate.getCurrency() != booking.getCurrency()) {
                throw new IllegalArgumentException("Le tarif '" + rate.getLabel() + "' est en " + rate.getCurrency()
                        + " : précisez le prix (basePrice) en " + booking.getCurrency());
            }
            booking.setBasePrice(rate.getPrice());
        }
        if (request.getDiscount() != null) {
            booking.setDiscount(VenueMoney.scale(request.getDiscount()));
        }
    }

    private BigDecimal computeTotal(VenueBooking booking, List<VenueBookingExtra> extras) {
        BigDecimal extrasTotal = extras.stream().map(VenueBookingExtra::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = booking.getBasePrice().add(extrasTotal).subtract(booking.getDiscount());
        if (total.signum() < 0) {
            throw new IllegalArgumentException("La remise dépasse le montant de la réservation");
        }
        return VenueMoney.scale(total);
    }

    private void assertNotOverpaid(VenueBooking booking) {
        BigDecimal paid = paidAmount(booking.getId());
        if (paid.compareTo(booking.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Le nouveau total (" + VenueMoney.format(booking.getTotalAmount(), booking.getCurrency())
                    + ") serait inférieur au montant déjà encaissé (" + VenueMoney.format(paid, booking.getCurrency()) + ")");
        }
    }

    private void validatePeriod(VenueBooking booking) {
        if (!booking.getEndAt().isAfter(booking.getStartAt())) {
            throw new IllegalArgumentException("La fin de la réservation doit être après son début");
        }
    }

    private void assertNoOverlap(VenueBooking booking) {
        List<VenueBooking> conflicts = bookingRepository.findOverlapping(booking.getVenueId(),
                booking.getStartAt(), booking.getEndAt(), VenueBookingStatus.BLOCKING,
                booking.getId() == null ? -1L : booking.getId());
        if (!conflicts.isEmpty()) {
            VenueBooking other = conflicts.get(0);
            throw new ConflictException("La salle est déjà réservée sur ce créneau : " + reference(other) + " ("
                    + other.getStatus() + ") du " + DATE_TIME.format(other.getStartAt())
                    + " au " + DATE_TIME.format(other.getEndAt()));
        }
    }

    private void assertEditable(VenueBooking booking) {
        if (booking.getStatus() == VenueBookingStatus.ANNULEE || booking.getStatus() == VenueBookingStatus.REALISEE) {
            throw new IllegalArgumentException("Une réservation " + booking.getStatus() + " ne peut plus être modifiée");
        }
    }

    private void transition(VenueBooking booking, VenueBookingStatus target) {
        if (!booking.getStatus().canTransitionTo(target)) {
            throw new IllegalArgumentException("Transition de statut invalide : " + booking.getStatus() + " → " + target);
        }
        booking.setStatus(target);
    }

    private void cancelInternal(VenueBooking booking, String reason) {
        transition(booking, VenueBookingStatus.ANNULEE);
        booking.setCancelReason(reason);
        booking.setOptionExpiresAt(null);
        bookingRepository.save(booking);
        eventLinkService.cancelLinkedEvent(booking);
        audit("BOOKING_CANCEL", booking, "Réservation " + reference(booking) + " annulée"
                + (reason == null ? "" : " : " + reason));
    }

    private VenueBookingResponse baseResponse(VenueBooking b, Venue venue, VenueClient client,
                                              BigDecimal paid, List<VenueBookingExtra> extras) {
        BigDecimal extrasTotal = extras == null
                ? b.getTotalAmount().subtract(b.getBasePrice()).add(b.getDiscount())
                : extras.stream().map(VenueBookingExtra::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidScaled = VenueMoney.scale(paid);
        return VenueBookingResponse.builder()
                .id(b.getId())
                .reference(reference(b))
                .venueId(b.getVenueId())
                .venueName(venue == null ? null : venue.getName())
                .clientId(b.getClientId())
                .clientName(client == null ? null : client.getFullName())
                .clientPhone(client == null ? null : client.getPhone())
                .title(b.getTitle())
                .eventType(b.getEventType())
                .startAt(b.getStartAt())
                .endAt(b.getEndAt())
                .guestCount(b.getGuestCount())
                .status(b.getStatus())
                .currency(b.getCurrency())
                .rateId(b.getRateId())
                .basePrice(b.getBasePrice())
                .extrasTotal(VenueMoney.scale(extrasTotal))
                .discount(b.getDiscount())
                .totalAmount(b.getTotalAmount())
                .paidAmount(paidScaled)
                .balance(VenueMoney.scale(b.getTotalAmount().subtract(paidScaled)))
                .optionExpiresAt(b.getOptionExpiresAt())
                .notes(b.getNotes())
                .cancelReason(b.getCancelReason())
                .eventId(b.getEventId())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private VenuePaymentResponse toPaymentResponse(VenueBookingPayment p) {
        return VenuePaymentResponse.builder()
                .id(p.getId())
                .bookingId(p.getBookingId())
                .receiptNumber(receiptNumber(p))
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .exchangeRate(p.getExchangeRate())
                .amountInBookingCurrency(p.getAmountInBookingCurrency())
                .method(p.getMethod())
                .reference(p.getReference())
                .paidAt(p.getPaidAt())
                .note(p.getNote())
                .build();
    }

    private void audit(String action, VenueBooking booking, String details) {
        auditService.record(action, booking.getId(), "VenueBooking", securityUtils.getCurrentUserId(),
                booking.getOrganizationId(), details);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
