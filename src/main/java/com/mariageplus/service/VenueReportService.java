package com.mariageplus.service;

import com.mariageplus.dto.venue.CurrencyAmount;
import com.mariageplus.dto.venue.VenueCashierTotal;
import com.mariageplus.dto.venue.VenueBookingResponse;
import com.mariageplus.dto.venue.VenueDashboardResponse;
import com.mariageplus.dto.venue.VenueExpenseListResponse;
import com.mariageplus.dto.venue.VenueRefundLine;
import com.mariageplus.entity.*;
import com.mariageplus.repository.VenueBookingPaymentRepository;
import com.mariageplus.repository.VenueBookingRepository;
import com.mariageplus.repository.VenueClientRepository;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tableau de bord et export Excel du module « Salles ». */
@Service
@RequiredArgsConstructor
public class VenueReportService {

    private static final Set<VenueBookingStatus> FIRM = EnumSet.of(VenueBookingStatus.CONFIRMEE, VenueBookingStatus.REALISEE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VenueBookingRepository bookingRepository;
    private final VenueBookingPaymentRepository paymentRepository;
    private final VenueRepository venueRepository;
    private final VenueClientRepository clientRepository;
    private final VenueBookingService bookingService;
    private final VenueModuleGuard guard;
    private final UserRepository userRepository;
    private final VenueExpenseService expenseService;

    /**
     * @param from premier jour inclus (défaut : 1er du mois courant)
     * @param to   dernier jour inclus (défaut : dernier jour du mois de {@code from})
     */
    @Transactional(readOnly = true)
    public VenueDashboardResponse dashboard(LocalDate from, LocalDate to, Long venueId) {
        Long orgId = guard.requireEnabledOrganization("VENUE_REPORT_VIEW");
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La date de fin doit être après la date de début");
        }
        LocalDateTime periodStart = start.atStartOfDay();
        LocalDateTime periodEnd = end.plusDays(1).atStartOfDay();

        List<Venue> venues = venueRepository.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(v -> venueId == null || v.getId().equals(venueId))
                .filter(Venue::isActive)
                .toList();

        List<VenueBooking> inPeriod = bookingRepository.findByOrganizationInPeriod(orgId, periodStart, periodEnd).stream()
                .filter(b -> venueId == null || b.getVenueId().equals(venueId))
                .toList();

        // Statuts des réservations qui COMMENCENT dans la période.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (VenueBookingStatus s : VenueBookingStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        inPeriod.stream().filter(b -> !b.getStartAt().isBefore(periodStart))
                .forEach(b -> byStatus.merge(b.getStatus().name(), 1L, Long::sum));

        // Valeur réservée (confirmées / réalisées commençant dans la période).
        Map<VenueCurrency, BigDecimal> booked = new EnumMap<>(VenueCurrency.class);
        inPeriod.stream().filter(b -> FIRM.contains(b.getStatus()) && !b.getStartAt().isBefore(periodStart))
                .forEach(b -> booked.merge(b.getCurrency(), b.getTotalAmount(), BigDecimal::add));

        // Encaissements de la période, par devise de paiement.
        Map<Long, VenueBooking> bookingsById = new HashMap<>();
        List<VenueBookingPayment> payments = paymentRepository.findByOrganizationPaidBetween(orgId, periodStart, periodEnd);
        if (venueId != null && !payments.isEmpty()) {
            bookingRepository.findAllById(payments.stream().map(VenueBookingPayment::getBookingId).collect(Collectors.toSet()))
                    .forEach(b -> bookingsById.put(b.getId(), b));
            payments = payments.stream().filter(p -> {
                VenueBooking b = bookingsById.get(p.getBookingId());
                return b != null && b.getVenueId().equals(venueId);
            }).toList();
        }
        Map<VenueCurrency, BigDecimal> collected = new EnumMap<>(VenueCurrency.class);
        Map<VenueCurrency, BigDecimal> refundedTotals = new EnumMap<>(VenueCurrency.class);
        payments.forEach(p -> {
            boolean refund = p.getKind() == VenuePaymentKind.REFUND;
            collected.merge(p.getCurrency(), refund ? p.getAmount().negate() : p.getAmount(), BigDecimal::add);
            if (refund) {
                refundedTotals.merge(p.getCurrency(), p.getAmount(), BigDecimal::add);
            }
        });

        // Dépenses de la période (filtrées par salle : les dépenses générales ne sont alors pas comptées).
        List<VenueExpense> expenses = expenseService.between(orgId, periodStart, periodEnd).stream()
                .filter(e -> venueId == null || venueId.equals(e.getVenueId()))
                .toList();
        List<CurrencyAmount> collectedList = toList(collected);
        List<CurrencyAmount> spent = VenueExpenseService.totals(expenses);
        Map<String, VenueExpenseListResponse.CategoryTotal> byCategory = new LinkedHashMap<>();
        expenses.forEach(e -> {
            VenueExpenseListResponse.CategoryTotal t = byCategory.computeIfAbsent(e.getCategory() + "|" + e.getCurrency(),
                    k -> VenueExpenseListResponse.CategoryTotal.builder()
                            .category(e.getCategory()).currency(e.getCurrency()).amount(BigDecimal.ZERO).build());
            t.setAmount(t.getAmount().add(e.getAmount()));
            t.setCount(t.getCount() + 1);
        });

        // Occupation : jours-salle couverts par des réservations fermes.
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long available = days * venues.size();
        Set<Long> venueIds = venues.stream().map(Venue::getId).collect(Collectors.toSet());
        Map<Long, Set<LocalDate>> occupied = new HashMap<>();
        inPeriod.stream().filter(b -> FIRM.contains(b.getStatus()) && venueIds.contains(b.getVenueId()))
                .forEach(b -> {
                    LocalDate d = max(b.getStartAt().toLocalDate(), start);
                    LocalDate last = min(b.getEndAt().minusSeconds(1).toLocalDate(), end);
                    for (; !d.isAfter(last); d = d.plusDays(1)) {
                        occupied.computeIfAbsent(b.getVenueId(), k -> new HashSet<>()).add(d);
                    }
                });
        long occupiedDays = occupied.values().stream().mapToLong(Set::size).sum();
        double rate = available == 0 ? 0.0
                : BigDecimal.valueOf(occupiedDays * 100.0 / available).setScale(1, RoundingMode.HALF_UP).doubleValue();

        // Impayés : toutes les réservations fermes avec un reste à payer.
        List<VenueBooking> firm = bookingRepository.findByOrganizationIdAndStatusIn(orgId, FIRM).stream()
                .filter(b -> venueId == null || b.getVenueId().equals(venueId))
                .toList();
        List<VenueBookingResponse> unpaid = bookingService.toSummaries(firm).stream()
                .filter(r -> r.getBalance().signum() > 0)
                .sorted(Comparator.comparing(VenueBookingResponse::getStartAt))
                .toList();
        Map<VenueCurrency, BigDecimal> outstanding = new EnumMap<>(VenueCurrency.class);
        unpaid.forEach(r -> outstanding.merge(r.getCurrency(), r.getBalance(), BigDecimal::add));

        // À venir : 30 prochains jours.
        LocalDateTime now = LocalDateTime.now();
        List<VenueBooking> upcoming = bookingRepository.findByOrganizationInPeriod(orgId, now, now.plusDays(30)).stream()
                .filter(b -> venueId == null || b.getVenueId().equals(venueId))
                .filter(b -> b.getStatus() == VenueBookingStatus.OPTION || b.getStatus() == VenueBookingStatus.CONFIRMEE)
                .toList();

        return VenueDashboardResponse.builder()
                .from(start)
                .to(end)
                .venueId(venueId)
                .venueCount(venues.size())
                .bookingsByStatus(byStatus)
                .collected(collectedList)
                .spent(spent)
                .result(VenueMoney.subtract(collectedList, spent))
                .expensesByCategory(byCategory.values().stream()
                        .peek(t -> t.setAmount(VenueMoney.scale(t.getAmount())))
                        .sorted(Comparator.comparing(VenueExpenseListResponse.CategoryTotal::getAmount).reversed())
                        .toList())
                .expenseCount(expenses.size())
                .refunded(toList(refundedTotals))
                .refunds(refundLines(payments))
                .byCashier(byCashier(orgId, venueId, payments, expenses))
                .bookedValue(toList(booked))
                .outstanding(toList(outstanding))
                .occupancyRate(rate)
                .occupiedVenueDays(occupiedDays)
                .availableVenueDays(available)
                .unpaid(unpaid.stream().limit(50).toList())
                .upcoming(bookingService.toSummaries(upcoming))
                .build();
    }

    /** Export Excel : feuille des réservations de la période et feuille des paiements encaissés. */
    @Transactional(readOnly = true)
    public byte[] exportXlsx(LocalDate from, LocalDate to) {
        Long orgId = guard.requireEnabledOrganization("VENUE_REPORT_VIEW");
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
        LocalDateTime periodStart = start.atStartOfDay();
        LocalDateTime periodEnd = end.plusDays(1).atStartOfDay();

        List<VenueBookingResponse> bookings = bookingService.toSummaries(
                bookingRepository.findByOrganizationInPeriod(orgId, periodStart, periodEnd));
        List<VenueBookingPayment> payments = paymentRepository.findByOrganizationPaidBetween(orgId, periodStart, periodEnd);
        Map<Long, VenueBooking> paymentBookings = bookingRepository.findAllById(
                        payments.stream().map(VenueBookingPayment::getBookingId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueBooking::getId, Function.identity()));
        Map<Long, VenueClient> clients = clientRepository.findByIdIn(paymentBookings.values().stream()
                        .map(VenueBooking::getClientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueClient::getId, Function.identity()));

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);

            List<List<Object>> bookingRows = bookings.stream().map(b -> List.<Object>of(
                    b.getReference(), nz(b.getVenueName()), nz(b.getClientName()), nz(b.getClientPhone()),
                    nz(b.getTitle()), DATE_TIME.format(b.getStartAt()), DATE_TIME.format(b.getEndAt()),
                    b.getStatus().name(), b.getCurrency().name(), b.getTotalAmount(), b.getPaidAmount(), b.getBalance()))
                    .toList();
            sheet(wb, head, "Réservations", List.of("Référence", "Salle", "Client", "Téléphone", "Titre", "Début", "Fin",
                    "Statut", "Devise", "Total", "Payé", "Reste"), bookingRows);

            List<List<Object>> paymentRows = payments.stream().map(p -> {
                VenueBooking b = paymentBookings.get(p.getBookingId());
                VenueClient c = b == null ? null : clients.get(b.getClientId());
                boolean refund = p.getKind() == VenuePaymentKind.REFUND;
                return List.<Object>of(VenueBookingService.receiptNumber(p), refund ? "Remboursement" : "Paiement",
                        DATE_TIME.format(p.getPaidAt()),
                        b == null ? "" : VenueBookingService.reference(b), c == null ? "" : c.getFullName(),
                        refund ? p.getAmount().negate() : p.getAmount(), p.getCurrency().name(),
                        p.getExchangeRate() == null ? "" : p.getExchangeRate(), p.getMethod().name(), nz(p.getReference()));
            }).toList();
            sheet(wb, head, "Paiements", List.of("Reçu", "Type", "Date", "Réservation", "Client", "Montant", "Devise",
                    "Taux (CDF/USD)", "Mode", "Référence"), paymentRows);

            List<List<Object>> expenseRows = expenseService.toResponses(expenseService.between(orgId, periodStart, periodEnd))
                    .stream().map(e -> List.<Object>of(DATE_TIME.format(e.getSpentAt()), e.getCategory().name(), e.getLabel(),
                            nz(e.getVenueName()), e.getAmount(), e.getCurrency().name(), e.getMethod().name(),
                            nz(e.getReference()), nz(e.getRecordedByName()), nz(e.getNote())))
                    .toList();
            sheet(wb, head, "Dépenses", List.of("Date", "Poste", "Libellé", "Salle", "Montant", "Devise", "Mode",
                    "Référence", "Saisie par", "Note"), expenseRows);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de générer le fichier Excel", ex);
        }
    }

    /**
     * Encaissements par personne sur la période (et aujourd'hui), nets des
     * remboursements, avec la part espèces / Mobile Money / virement : le gérant
     * rapproche ainsi la caisse de chaque employé.
     */
    private List<VenueCashierTotal> byCashier(Long orgId, Long venueId, List<VenueBookingPayment> periodPayments,
                                              List<VenueExpense> periodExpenses) {
        LocalDate today = LocalDate.now();
        List<VenueBookingPayment> todays = paymentRepository.findByOrganizationPaidBetween(
                orgId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (venueId != null && !todays.isEmpty()) {
            Set<Long> venueBookings = bookingRepository.findAllById(
                            todays.stream().map(VenueBookingPayment::getBookingId).collect(Collectors.toSet())).stream()
                    .filter(b -> b.getVenueId().equals(venueId)).map(VenueBooking::getId).collect(Collectors.toSet());
            todays = todays.stream().filter(p -> venueBookings.contains(p.getBookingId())).toList();
        }
        Map<Long, List<VenueBookingPayment>> period = periodPayments.stream().filter(p -> p.getRecordedBy() != null)
                .collect(Collectors.groupingBy(VenueBookingPayment::getRecordedBy, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<VenueBookingPayment>> ofToday = todays.stream().filter(p -> p.getRecordedBy() != null)
                .collect(Collectors.groupingBy(VenueBookingPayment::getRecordedBy));
        Map<Long, List<VenueExpense>> spentBy = periodExpenses.stream().filter(e -> e.getRecordedBy() != null)
                .collect(Collectors.groupingBy(VenueExpense::getRecordedBy));
        Set<Long> userIds = new LinkedHashSet<>(period.keySet());
        userIds.addAll(ofToday.keySet());
        userIds.addAll(spentBy.keySet());
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> (nz(u.getFirstName()) + " " + nz(u.getLastName())).trim()));
        Set<VenuePaymentMethod> mobile = EnumSet.of(VenuePaymentMethod.MPESA, VenuePaymentMethod.ORANGE_MONEY,
                VenuePaymentMethod.AIRTEL_MONEY);
        return userIds.stream().map(id -> {
            List<VenueBookingPayment> mine = period.getOrDefault(id, List.of());
            List<VenueExpense> myExpenses = spentBy.getOrDefault(id, List.of());
            List<CurrencyAmount> cash = netTotals(mine.stream().filter(p -> p.getMethod() == VenuePaymentMethod.ESPECES).toList());
            return VenueCashierTotal.builder()
                    .userId(id)
                    .name(names.getOrDefault(id, "Utilisateur #" + id))
                    .today(netTotals(ofToday.getOrDefault(id, List.of())))
                    .collected(netTotals(mine))
                    .cash(cash)
                    .spent(VenueExpenseService.totals(myExpenses))
                    .cashInHand(VenueMoney.subtract(cash, VenueExpenseService.totals(myExpenses.stream()
                            .filter(e -> e.getMethod() == VenuePaymentMethod.ESPECES).toList())))
                    .mobileMoney(netTotals(mine.stream().filter(p -> mobile.contains(p.getMethod())).toList()))
                    .transfer(netTotals(mine.stream().filter(p -> p.getMethod() == VenuePaymentMethod.VIREMENT).toList()))
                    .paymentCount(mine.stream().filter(p -> p.getKind() != VenuePaymentKind.REFUND).count())
                    .refundCount(mine.stream().filter(p -> p.getKind() == VenuePaymentKind.REFUND).count())
                    .build();
        }).sorted(Comparator.comparing(VenueCashierTotal::getPaymentCount).reversed()).toList();
    }

    private static List<CurrencyAmount> netTotals(List<VenueBookingPayment> payments) {
        Map<VenueCurrency, BigDecimal> sums = new EnumMap<>(VenueCurrency.class);
        payments.forEach(p -> sums.merge(p.getCurrency(),
                p.getKind() == VenuePaymentKind.REFUND ? p.getAmount().negate() : p.getAmount(), BigDecimal::add));
        return toList(sums);
    }

    /** Lignes des remboursements (réservation, client, salle) pour la liste du tableau de bord. */
    private List<VenueRefundLine> refundLines(List<VenueBookingPayment> payments) {
        List<VenueBookingPayment> refunds = payments.stream()
                .filter(p -> p.getKind() == VenuePaymentKind.REFUND)
                .sorted(Comparator.comparing(VenueBookingPayment::getPaidAt).reversed())
                .toList();
        if (refunds.isEmpty()) {
            return List.of();
        }
        Map<Long, VenueBooking> bookings = bookingRepository.findAllById(
                        refunds.stream().map(VenueBookingPayment::getBookingId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueBooking::getId, Function.identity()));
        Map<Long, VenueClient> clients = clientRepository.findByIdIn(bookings.values().stream()
                        .map(VenueBooking::getClientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VenueClient::getId, Function.identity()));
        Map<Long, Venue> venues = venueRepository.findAllById(bookings.values().stream()
                        .map(VenueBooking::getVenueId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        return refunds.stream().map(p -> {
            VenueBooking b = bookings.get(p.getBookingId());
            VenueClient c = b == null ? null : clients.get(b.getClientId());
            Venue v = b == null ? null : venues.get(b.getVenueId());
            return VenueRefundLine.builder()
                    .paymentId(p.getId())
                    .receiptNumber(VenueBookingService.receiptNumber(p))
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
        }).toList();
    }

    private void sheet(Workbook wb, CellStyle head, String name, List<String> headers, List<List<Object>> rows) {
        Sheet sheet = wb.createSheet(name);
        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headers.get(c));
            cell.setCellStyle(head);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            List<Object> values = rows.get(r);
            for (int c = 0; c < values.size(); c++) {
                Object v = values.get(c);
                Cell cell = row.createCell(c);
                if (v instanceof BigDecimal n) {
                    cell.setCellValue(n.doubleValue());
                } else {
                    cell.setCellValue(v == null ? "" : String.valueOf(v));
                }
            }
        }
        for (int c = 0; c < headers.size(); c++) {
            sheet.autoSizeColumn(c);
        }
        sheet.createFreezePane(0, 1);
    }

    private static List<CurrencyAmount> toList(Map<VenueCurrency, BigDecimal> totals) {
        return totals.entrySet().stream()
                .map(e -> CurrencyAmount.builder().currency(e.getKey()).amount(VenueMoney.scale(e.getValue())).build())
                .toList();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
