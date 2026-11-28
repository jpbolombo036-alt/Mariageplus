package com.mariageplus.service;

import com.mariageplus.dto.venue.CurrencyAmount;
import com.mariageplus.dto.venue.VenueExpenseListResponse;
import com.mariageplus.dto.venue.VenueExpenseRequest;
import com.mariageplus.dto.venue.VenueExpenseResponse;
import com.mariageplus.entity.*;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.UserRepository;
import com.mariageplus.repository.VenueExpenseRepository;
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
 * Dépenses de salle. Le caissier (EXPENSE_RECORD) saisit ses sorties d'argent et
 * ne voit que les siennes ; le gérant (EXPENSE_MANAGE) voit tout, modifie et
 * annule. Les dépenses alimentent « Ma caisse » et le tableau de bord.
 */
@Service
@RequiredArgsConstructor
public class VenueExpenseService {

    static final String RECORD = "EXPENSE_RECORD";
    static final String MANAGE = "EXPENSE_MANAGE";

    private final VenueExpenseRepository repository;
    private final VenueRepository venueRepository;
    private final UserRepository userRepository;
    private final VenueModuleGuard guard;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /**
     * @param from premier jour inclus (défaut : 1er du mois courant)
     * @param to   dernier jour inclus (défaut : dernier jour du mois de {@code from})
     */
    @Transactional(readOnly = true)
    public VenueExpenseListResponse list(LocalDate from, LocalDate to, Long venueId, VenueExpenseCategory category) {
        Long orgId = guard.requireEnabledOrganization(RECORD);
        boolean all = securityUtils.hasPermission(MANAGE);
        Long userId = securityUtils.getCurrentUserId();
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La date de fin doit être après la date de début");
        }
        List<VenueExpense> expenses = between(orgId, start.atStartOfDay(), end.plusDays(1).atStartOfDay()).stream()
                .filter(e -> all || Objects.equals(userId, e.getRecordedBy()))
                .filter(e -> venueId == null || venueId.equals(e.getVenueId()))
                .filter(e -> category == null || category == e.getCategory())
                .toList();

        Map<String, VenueExpenseListResponse.CategoryTotal> byCategory = new LinkedHashMap<>();
        expenses.forEach(e -> {
            VenueExpenseListResponse.CategoryTotal t = byCategory.computeIfAbsent(e.getCategory() + "|" + e.getCurrency(),
                    k -> VenueExpenseListResponse.CategoryTotal.builder()
                            .category(e.getCategory()).currency(e.getCurrency()).amount(BigDecimal.ZERO).build());
            t.setAmount(t.getAmount().add(e.getAmount()));
            t.setCount(t.getCount() + 1);
        });
        List<VenueExpenseListResponse.CategoryTotal> categories = byCategory.values().stream()
                .peek(t -> t.setAmount(VenueMoney.scale(t.getAmount())))
                .sorted(Comparator.comparing(VenueExpenseListResponse.CategoryTotal::getAmount).reversed())
                .toList();

        return VenueExpenseListResponse.builder()
                .from(start)
                .to(end)
                .allRecorders(all)
                .items(toResponses(expenses))
                .total(totals(expenses))
                .byCategory(categories)
                .build();
    }

    @Transactional
    public VenueExpenseResponse create(VenueExpenseRequest request) {
        Long orgId = guard.requireEnabledOrganization(RECORD);
        VenueExpense expense = VenueExpense.builder()
                .organizationId(orgId)
                .recordedBy(securityUtils.getCurrentUserId())
                .build();
        apply(expense, request, orgId);
        VenueExpense saved = repository.save(expense);
        auditService.record("VENUE_EXPENSE_CREATED", saved.getId(), "VenueExpense", securityUtils.getCurrentUserId(),
                orgId, describe(saved));
        return toResponses(List.of(saved)).get(0);
    }

    @Transactional
    public VenueExpenseResponse update(Long id, VenueExpenseRequest request) {
        Long orgId = guard.requireEnabledOrganization(MANAGE);
        VenueExpense expense = find(id, orgId);
        String before = describe(expense);
        apply(expense, request, orgId);
        VenueExpense saved = repository.save(expense);
        auditService.record("VENUE_EXPENSE_UPDATED", saved.getId(), "VenueExpense", securityUtils.getCurrentUserId(),
                orgId, before + " → " + describe(saved));
        return toResponses(List.of(saved)).get(0);
    }

    /** Annulation (suppression douce) : la dépense sort des totaux, l'audit garde la trace. */
    @Transactional
    public void cancel(Long id, String reason) {
        Long orgId = guard.requireEnabledOrganization(MANAGE);
        VenueExpense expense = find(id, orgId);
        expense.softDelete();
        repository.save(expense);
        auditService.record("VENUE_EXPENSE_CANCELLED", expense.getId(), "VenueExpense", securityUtils.getCurrentUserId(),
                orgId, "Dépense annulée : " + describe(expense)
                        + (reason == null || reason.isBlank() ? "" : " — motif : " + reason.trim()));
    }

    // ---- Utilisé par « Ma caisse » et le tableau de bord (accès déjà vérifié) ----

    public List<VenueExpense> between(Long organizationId, LocalDateTime from, LocalDateTime to) {
        return repository.findByOrganizationIdAndSpentAtGreaterThanEqualAndSpentAtLessThanOrderBySpentAtDescIdDesc(
                organizationId, from, to);
    }

    public static List<CurrencyAmount> totals(Collection<VenueExpense> expenses) {
        Map<VenueCurrency, BigDecimal> sums = new EnumMap<>(VenueCurrency.class);
        expenses.forEach(e -> sums.merge(e.getCurrency(), e.getAmount(), BigDecimal::add));
        return sums.entrySet().stream()
                .map(e -> CurrencyAmount.builder().currency(e.getKey()).amount(VenueMoney.scale(e.getValue())).build())
                .toList();
    }

    public List<VenueExpenseResponse> toResponses(List<VenueExpense> expenses) {
        if (expenses.isEmpty()) {
            return List.of();
        }
        Map<Long, String> venues = venueRepository.findAllById(expenses.stream().map(VenueExpense::getVenueId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
        Map<Long, User> users = userRepository.findAllById(expenses.stream().map(VenueExpense::getRecordedBy)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return expenses.stream().map(e -> {
            User u = e.getRecordedBy() == null ? null : users.get(e.getRecordedBy());
            return VenueExpenseResponse.builder()
                    .id(e.getId())
                    .category(e.getCategory())
                    .label(e.getLabel())
                    .amount(VenueMoney.scale(e.getAmount()))
                    .currency(e.getCurrency())
                    .method(e.getMethod())
                    .venueId(e.getVenueId())
                    .venueName(e.getVenueId() == null ? null : venues.get(e.getVenueId()))
                    .reference(e.getReference())
                    .spentAt(e.getSpentAt())
                    .note(e.getNote())
                    .recordedBy(e.getRecordedBy())
                    .recordedByName(u == null ? null : (nz(u.getFirstName()) + " " + nz(u.getLastName())).trim())
                    .build();
        }).toList();
    }

    private void apply(VenueExpense expense, VenueExpenseRequest request, Long orgId) {
        if (request.getVenueId() != null) {
            Venue venue = venueRepository.findById(request.getVenueId())
                    .filter(v -> orgId.equals(v.getOrganizationId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Salle introuvable"));
            expense.setVenueId(venue.getId());
        } else {
            expense.setVenueId(null);
        }
        LocalDateTime spentAt = request.getSpentAt() != null ? request.getSpentAt() : LocalDateTime.now();
        if (spentAt.isAfter(LocalDateTime.now().plusDays(1))) {
            throw new IllegalArgumentException("La date de la dépense ne peut pas être dans le futur");
        }
        expense.setCategory(request.getCategory());
        expense.setLabel(request.getLabel().trim());
        expense.setAmount(VenueMoney.scale(request.getAmount()));
        expense.setCurrency(request.getCurrency());
        expense.setMethod(request.getMethod() != null ? request.getMethod() : VenuePaymentMethod.ESPECES);
        expense.setReference(blankToNull(request.getReference()));
        expense.setSpentAt(spentAt);
        expense.setNote(blankToNull(request.getNote()));
    }

    private VenueExpense find(Long id, Long orgId) {
        return repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense introuvable"));
    }

    private static String describe(VenueExpense e) {
        return e.getLabel() + " (" + e.getCategory() + ") " + VenueMoney.scale(e.getAmount()) + " " + e.getCurrency();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
