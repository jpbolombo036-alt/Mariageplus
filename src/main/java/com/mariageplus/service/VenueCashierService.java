package com.mariageplus.service;

import com.mariageplus.dto.venue.CurrencyAmount;
import com.mariageplus.dto.venue.VenueCashierSummary;
import com.mariageplus.dto.venue.VenueExpenseResponse;
import com.mariageplus.entity.*;
import com.mariageplus.repository.VenueBookingPaymentRepository;
import com.mariageplus.repository.VenueBookingRepository;
import com.mariageplus.repository.VenueClientRepository;
import com.mariageplus.repository.VenueRepository;
import com.mariageplus.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * « Ma caisse » : encaissements de l'utilisateur connecté uniquement. Un
 * caissier (AGENT_SALLE) suit sa caisse du jour et la rapproche de l'argent
 * réellement reçu, sans accès au chiffre d'affaires global (VENUE_REPORT_VIEW).
 */
@Service
@RequiredArgsConstructor
public class VenueCashierService {

    private final VenueBookingPaymentRepository paymentRepository;
    private final VenueBookingRepository bookingRepository;
    private final VenueClientRepository clientRepository;
    private final VenueRepository venueRepository;
    private final VenueBookingService bookingService;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final VenueExpenseService expenseService;

    /**
     * @param from premier jour inclus (défaut : 1er du mois courant)
     * @param to   dernier jour inclus (défaut : dernier jour du mois de {@code from})
     */
    @Transactional(readOnly = true)
    public VenueCashierSummary myCash(LocalDate from, LocalDate to) {
        Long orgId = guard.requireEnabledOrganization("BOOKING_PAYMENT_RECORD");
        Long userId = securityUtils.getCurrentUserId();
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La date de fin doit être après la date de début");
        }

        List<VenueBookingPayment> mine = mineBetween(orgId, userId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        LocalDate today = LocalDate.now();
        List<VenueBookingPayment> todays = mineBetween(orgId, userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        List<VenueExpense> myExpenses = myExpensesBetween(orgId, userId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<VenueExpense> todaysExpenses = myExpensesBetween(orgId, userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        // Par mode et par devise (net : un remboursement ou une dépense fait sortir l'argent du même mode).
        Map<String, VenueCashierSummary.MethodTotal> byMethod = new LinkedHashMap<>();
        for (VenueBookingPayment p : mine) {
            String key = p.getMethod() + "|" + p.getCurrency();
            VenueCashierSummary.MethodTotal t = byMethod.computeIfAbsent(key, k -> VenueCashierSummary.MethodTotal.builder()
                    .method(p.getMethod()).currency(p.getCurrency()).amount(BigDecimal.ZERO).count(0).build());
            t.setAmount(t.getAmount().add(signed(p)));
            t.setCount(t.getCount() + 1);
        }
        for (VenueExpense e : myExpenses) {
            String key = e.getMethod() + "|" + e.getCurrency();
            VenueCashierSummary.MethodTotal t = byMethod.computeIfAbsent(key, k -> VenueCashierSummary.MethodTotal.builder()
                    .method(e.getMethod()).currency(e.getCurrency()).amount(BigDecimal.ZERO).count(0).build());
            t.setAmount(t.getAmount().subtract(e.getAmount()));
            t.setCount(t.getCount() + 1);
        }
        byMethod.values().forEach(t -> t.setAmount(VenueMoney.scale(t.getAmount())));
        List<CurrencyAmount> collected = totals(mine);
        List<CurrencyAmount> spent = VenueExpenseService.totals(myExpenses);

        LocalDateTime now = LocalDateTime.now();
        List<VenueBooking> upcoming = bookingRepository.findByOrganizationInPeriod(orgId, now, now.plusDays(7)).stream()
                .filter(b -> b.getStatus() == VenueBookingStatus.OPTION || b.getStatus() == VenueBookingStatus.CONFIRMEE)
                .toList();

        return VenueCashierSummary.builder()
                .from(start)
                .to(end)
                .today(totals(todays))
                .collected(collected)
                .spent(spent)
                .todaySpent(VenueExpenseService.totals(todaysExpenses))
                .balance(VenueMoney.subtract(collected, spent))
                .expenseCount(myExpenses.size())
                .expenses(expenseService.toResponses(myExpenses))
                .paymentCount(mine.stream().filter(p -> p.getKind() != VenuePaymentKind.REFUND).count())
                .refundCount(mine.stream().filter(p -> p.getKind() == VenuePaymentKind.REFUND).count())
                .byMethod(new ArrayList<>(byMethod.values()))
                .movements(lines(mine))
                .upcoming(bookingService.toSummaries(upcoming))
                .build();
    }

    private List<VenueBookingPayment> mineBetween(Long orgId, Long userId, LocalDateTime from, LocalDateTime to) {
        return paymentRepository.findByOrganizationPaidBetween(orgId, from, to).stream()
                .filter(p -> userId != null && userId.equals(p.getRecordedBy()))
                .toList();
    }

    private List<VenueExpense> myExpensesBetween(Long orgId, Long userId, LocalDateTime from, LocalDateTime to) {
        return expenseService.between(orgId, from, to).stream()
                .filter(e -> userId != null && userId.equals(e.getRecordedBy()))
                .toList();
    }

    private static BigDecimal signed(VenueBookingPayment p) {
        return p.getKind() == VenuePaymentKind.REFUND ? p.getAmount().negate() : p.getAmount();
    }

    private static List<CurrencyAmount> totals(List<VenueBookingPayment> payments) {
        Map<VenueCurrency, BigDecimal> sums = new EnumMap<>(VenueCurrency.class);
        payments.forEach(p -> sums.merge(p.getCurrency(), signed(p), BigDecimal::add));
        return sums.entrySet().stream()
                .map(e -> CurrencyAmount.builder().currency(e.getKey()).amount(VenueMoney.scale(e.getValue())).build())
                .toList();
    }

    private List<VenueCashierSummary.CashLine> lines(List<VenueBookingPayment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, VenueBooking> bookings = bookingRepository.findAllById(
                        payments.stream().map(VenueBookingPayment::getBookingId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueBooking::getId, Function.identity()));
        Map<Long, VenueClient> clients = clientRepository.findByIdIn(bookings.values().stream()
                        .map(VenueBooking::getClientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueClient::getId, Function.identity()));
        Map<Long, Venue> venues = venueRepository.findAllById(bookings.values().stream()
                        .map(VenueBooking::getVenueId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        return payments.stream()
                .sorted(Comparator.comparing(VenueBookingPayment::getPaidAt).reversed())
                .map(p -> {
                    VenueBooking b = bookings.get(p.getBookingId());
                    VenueClient c = b == null ? null : clients.get(b.getClientId());
                    Venue v = b == null ? null : venues.get(b.getVenueId());
                    return VenueCashierSummary.CashLine.builder()
                            .paymentId(p.getId())
                            .receiptNumber(VenueBookingService.receiptNumber(p))
                            .kind(p.getKind())
                            .bookingId(p.getBookingId())
                            .bookingReference(b == null ? null : VenueBookingService.reference(b))
                            .clientName(c == null ? null : c.getFullName())
                            .venueName(v == null ? null : v.getName())
                            .amount(p.getAmount())
                            .currency(p.getCurrency())
                            .method(p.getMethod())
                            .reference(p.getReference())
                            .paidAt(p.getPaidAt())
                            .build();
                })
                .toList();
    }
}
